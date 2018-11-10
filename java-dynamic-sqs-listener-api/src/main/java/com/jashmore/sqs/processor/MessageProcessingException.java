package com.jashmore.sqs.processor;

/**
 * Exception thrown when the {@link MessageProcessor} has an exception processing a message.
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
