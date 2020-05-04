package com.jashmore.sqs.extensions.registry;

import org.springframework.cloud.schema.registry.SchemaReference;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Used to obtain the schema for a message that was sent from a producer.
 *
 * @param <T> the type of the schema, for example an Avro schema
 */
@ThreadSafe
@FunctionalInterface
public interface ProducerSchemaRetriever<T> {
    /**
     * Given the schema reference, obtain the Schema that represents the payload that was sent from the producer.
     *
     * <p>For example, given that it is an object of type "Signal.v2", return the schema for this so the consumer
     * will know how to transform this to the version that they have defined, e.g. "Signal.v3".
     *
     * @param reference the reference to the schema that the producer was using
     * @return the schema definition
     * @throws ProducerSchemaRetrieverException when there was an error getting the schema
     */
    T getSchema(SchemaReference reference) throws ProducerSchemaRetrieverException;
}
