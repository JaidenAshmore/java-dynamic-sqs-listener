package com.jashmore.sqs.extensions.registry.avro;

import com.jashmore.sqs.extensions.registry.ProducerSchemaRetriever;
import com.jashmore.sqs.extensions.registry.ProducerSchemaRetrieverException;
import org.apache.avro.Schema;
import org.springframework.cloud.schema.registry.SchemaReference;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;

/**
 * Implementation that uses the Spring Cloud Schema Registry to retrieve the schemas that the producer has sent
 * messages using.
 */
public class AvroSchemaRegistryProducerSchemaRetriever implements ProducerSchemaRetriever<Schema> {
    private final SchemaRegistryClient schemaRegistryClient;

    public AvroSchemaRegistryProducerSchemaRetriever(final SchemaRegistryClient schemaRegistryClient) {
        this.schemaRegistryClient = schemaRegistryClient;
    }

    @Override
    public Schema getSchema(final SchemaReference schemaReference) {
        try {
            final String schemaContent = schemaRegistryClient.fetch(schemaReference);
            return new Schema.Parser().parse(schemaContent);
        } catch (RuntimeException runtimeException) {
            throw new ProducerSchemaRetrieverException(runtimeException);
        }
    }
}
