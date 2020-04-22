package com.jashmore.sqs.extensions.registry.avro;

import com.jashmore.sqs.extensions.registry.producer.ProducerSchemaProvider;
import com.jashmore.sqs.extensions.registry.producer.ProducerSchemaRetrievalException;
import org.apache.avro.Schema;
import org.springframework.cloud.schema.registry.SchemaReference;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;

/**
 * Implementation that uses the Spring Cloud Schema Registry to retrieve the schemas from.
 */
public class AvroSchemaRegistryProducerSchemaProvider implements ProducerSchemaProvider<Schema> {
    private final SchemaRegistryClient schemaRegistryClient;

    public AvroSchemaRegistryProducerSchemaProvider(final SchemaRegistryClient schemaRegistryClient) {
        this.schemaRegistryClient = schemaRegistryClient;
    }

    @Override
    public Schema getSchema(final SchemaReference schemaReference) {
        try {
            final String schemaContent = schemaRegistryClient.fetch(schemaReference);
            return new Schema.Parser().parse(schemaContent);
        } catch (RuntimeException runtimeException) {
            throw new ProducerSchemaRetrievalException(runtimeException);
        }
    }
}
