package com.jashmore.sqs.micronaut.container;

/**
 * Exception that is thrown when their is a known error initialising a {@link com.jashmore.sqs.container.MessageListenerContainer}.
 */
public class MessageListenerContainerInitialisationException extends RuntimeException {

    public MessageListenerContainerInitialisationException(final String message) {
        super(message);
    }
}
