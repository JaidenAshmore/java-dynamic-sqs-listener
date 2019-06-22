package com.jashmore.sqs.spring;

/**
 * Exception that is thrown when their is a known error wrapping a queue listener.
 */
public class QueueWrapperInitialisationException extends RuntimeException {
    public QueueWrapperInitialisationException(final String message) {
        super(message);
    }
}
