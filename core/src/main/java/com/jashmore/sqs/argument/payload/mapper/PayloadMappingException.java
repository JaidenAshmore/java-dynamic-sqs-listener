package com.jashmore.sqs.argument.payload.mapper;

/**
 * Exception thrown when there was a problem casting the message content to a Java Bean.
 */
public class PayloadMappingException extends RuntimeException {
    public PayloadMappingException(String message) {
        super(message);
    }

    public PayloadMappingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
