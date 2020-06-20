package com.jashmore.sqs.processor;

/**
 * Exception thrown when the {@link MessageProcessor} has an exception processing a message.
 */
public class MessageProcessingException extends RuntimeException {
    public MessageProcessingException(final String message) {
        super(message);
    }

    public MessageProcessingException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public MessageProcessingException(final Throwable cause) {
        super(cause);
    }
}
