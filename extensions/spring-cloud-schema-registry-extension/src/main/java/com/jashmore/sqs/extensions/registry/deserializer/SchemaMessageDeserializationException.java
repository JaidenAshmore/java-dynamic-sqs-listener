package com.jashmore.sqs.extensions.registry.deserializer;

/**
 * Exception thrown when there was an error trying to deserialize the message payload to the required schema.
 */
public class SchemaMessageDeserializationException extends RuntimeException {
    public SchemaMessageDeserializationException(final Throwable cause) {
        super(cause);
    }
}
