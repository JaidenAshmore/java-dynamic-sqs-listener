package com.jashmore.sqs.micronaut.decorator;

import com.jashmore.sqs.decorator.MessageProcessingDecorator;

/**
 * An exception thrown if there was an error building the {@link MessageProcessingDecorator} or if the method being
 * wrapped is not in the correct shape.
 */
@SuppressWarnings("unused")
public class MessageProcessingDecoratorFactoryException extends RuntimeException {

    public MessageProcessingDecoratorFactoryException() {}

    public MessageProcessingDecoratorFactoryException(final String message) {
        super(message);
    }

    public MessageProcessingDecoratorFactoryException(final String message, Throwable cause) {
        super(message, cause);
    }

    public MessageProcessingDecoratorFactoryException(final Throwable cause) {
        super(cause);
    }

    public MessageProcessingDecoratorFactoryException(
        final String message,
        final Throwable cause,
        final boolean enableSuppression,
        final boolean writableStackTrace
    ) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
