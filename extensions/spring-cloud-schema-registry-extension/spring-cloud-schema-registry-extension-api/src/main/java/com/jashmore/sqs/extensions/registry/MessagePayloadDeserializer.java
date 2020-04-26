package com.jashmore.sqs.extensions.registry;

import software.amazon.awssdk.services.sqs.model.Message;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Used to deserialize the payload of a message into a POJO represented by the given schemas.
 *
 * @param <T> the type of the schema used to represent the object
 */
@ThreadSafe
@FunctionalInterface
public interface MessagePayloadDeserializer<T> {
    /**
     * Deserialize the message body of the message to the required object type.
     *
     * <p>This will take the content of the message body that was serialized in the schema defined in the producer schema
     * and deserialize it to this consumer's schema, returning the new object.
     *
     * <p>For example, in the producer schema there may have only  been a temperature field but in the new consumer schema there
     * is an internal and external temperature. For this case the new schema would have definitions of how to take the old schema
     * to produce the new schema, e.g. just put the temperature into the internal temperature and set external temperature as
     * zero.
     *
     * @param message        the message to deserialize
     * @param producerSchema the schema of the payload that was sent by the producer
     * @param consumerSchema the schema that the consumer can handle
     * @param clazz          the class of the object to deserialize to
     * @return the deserialized body
     * @throws MessagePayloadDeserializerException when there was an error deserializing
     */
    Object deserialize(Message message, T producerSchema, T consumerSchema, Class<?> clazz) throws MessagePayloadDeserializerException;
}
