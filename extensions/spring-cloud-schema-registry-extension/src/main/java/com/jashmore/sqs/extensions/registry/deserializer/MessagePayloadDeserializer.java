package com.jashmore.sqs.extensions.registry.deserializer;

import org.apache.avro.Schema;
import software.amazon.awssdk.services.sqs.model.Message;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@FunctionalInterface
public interface MessagePayloadDeserializer {
    /**
     * Deserialize the message body of the message to the required object type.
     *
     * <p>This will take the content of the message body that was serialized in the schema defined in the producer {@link Schema}
     * and deserialize it to this consumer's schema, returning the new object.
     *
     * <p>For example, in the producer schema there may have only  been a temperature field but in the new consumer schema there
     * is an internal and external temperature. For this case the new schema would have definitions of how to take the old schema
     * to produce the new schema, e.g. just put the temperature into the internal temperature and set external temperature as
     * zero.
     *
     * @param message        the message to deserialize
     * @param producerSchema the schema of the payload that was sent by the producer
     * @param consumerSchema the schema that the consumer can handle, this should be a later version than the producer
     * @param clazz          the class of the object to deserialize to
     * @return the deserialized body
     * @throws SchemaMessageDeserializationException when there was an error deserializing
     */
    Object deserialize(Message message, Schema producerSchema, Schema consumerSchema, Class<?> clazz) throws SchemaMessageDeserializationException;
}
