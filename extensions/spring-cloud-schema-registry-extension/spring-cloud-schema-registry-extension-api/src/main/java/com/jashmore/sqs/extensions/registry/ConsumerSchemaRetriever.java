package com.jashmore.sqs.extensions.registry;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Provider for getting a schema representation of an object.
 *
 * @param <T> the type for the schema, for example an Avro schema, etc
 */
@ThreadSafe
@FunctionalInterface
public interface ConsumerSchemaRetriever<T> {
    /**
     * Get the schema representation for the provided class.
     *
     * <p>This will be used by the {@link MessagePayloadDeserializer} to determine how it can transform the message
     * that may be have been produced with a different version of the schema.
     *
     * @param clazz the class of the object to get the schema for
     * @return the schema definition for this class
     */
    T getSchema(Class<?> clazz);
}
