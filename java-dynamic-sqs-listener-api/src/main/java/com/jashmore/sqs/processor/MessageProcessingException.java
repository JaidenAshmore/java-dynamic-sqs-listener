package com.jashmore.sqs.processor;

/**
 * Exception that wraps the exception thrown from the underlying method that handles the message.
 */
public class MessageProcessingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MessageProcessingException(final String message) {
        super(message);
    }

    public MessageProcessingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
