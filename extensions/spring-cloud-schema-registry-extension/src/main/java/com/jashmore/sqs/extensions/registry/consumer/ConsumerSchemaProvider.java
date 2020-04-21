package com.jashmore.sqs.extensions.registry.consumer;

import com.jashmore.sqs.extensions.registry.deserializer.MessagePayloadDeserializer;
import org.apache.avro.Schema;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@FunctionalInterface
public interface ConsumerSchemaProvider {
    /**
     * Get the {@link Schema} definition for the class in the classpath.
     *
     * <p>This will be used by the {@link MessagePayloadDeserializer} to determine how it can transform the message
     * that may be have been produced with an older version of the schema.
     *
     * @param clazz the class of the object to get the schema for
     * @return the schema definition for this class
     */
    Schema get(Class<?> clazz);
}
