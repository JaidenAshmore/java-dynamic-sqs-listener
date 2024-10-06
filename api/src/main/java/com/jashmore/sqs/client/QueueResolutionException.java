package com.jashmore.sqs.client;

/**
 * Exception thrown when there was a problem resolving the URL for a queue.
 */
public class QueueResolutionException extends RuntimeException {

    public QueueResolutionException(final Throwable cause) {
        super(cause);
    }
}
