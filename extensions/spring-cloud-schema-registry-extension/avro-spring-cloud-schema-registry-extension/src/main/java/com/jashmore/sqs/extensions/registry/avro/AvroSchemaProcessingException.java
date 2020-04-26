package com.jashmore.sqs.extensions.registry.avro;

/**
 * Exception thrown when there was a problem processing the Avro schema.
 */
public class AvroSchemaProcessingException extends RuntimeException {
    public AvroSchemaProcessingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
