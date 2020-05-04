package com.jashmore.sqs.extensions.registry;

/**
 * Exception thrown when there was an error trying to deserialize the message payload to the required schema.
 */
public class MessagePayloadDeserializerException extends RuntimeException {
    public MessagePayloadDeserializerException(final Throwable cause) {
        super(cause);
    }
}
