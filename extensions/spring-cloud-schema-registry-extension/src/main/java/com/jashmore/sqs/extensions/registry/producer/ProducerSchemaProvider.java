package com.jashmore.sqs.extensions.registry.producer;

import org.apache.avro.Schema;
import org.springframework.cloud.schema.registry.SchemaReference;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@FunctionalInterface
public interface ProducerSchemaProvider {
    /**
     * Given the schema reference, obtain the Schema that represents the payload that was sent from the producer.
     *
     * <p>For example, given that it is an object of type "Signal.v2", return the schema for this so the consumer
     * will know how to transform this to the version that they have defined, e.g. "Signal.v3".
     *
     * @param reference the reference to the schema that the producer was using
     * @return the schema definition
     * @throws ProducerSchemaRetrievalException when there was an error getting the schema
     */
    Schema getSchema(SchemaReference reference) throws ProducerSchemaRetrievalException;
}
