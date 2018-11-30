package com.jashmore.sqs.argument;

/**
 * Exception thrown when there was an error resolving an argument for the method.
 *
 * <p>This can occur if a payload is trying to be resolved but the message does not conform and cannot be correctly created. For example,
 * you have a Java Bean being built from the message body but the message is not in the correct format.
 */
public class ArgumentResolutionException extends RuntimeException {
    public ArgumentResolutionException(final String message) {
        super(message);
    }

    public ArgumentResolutionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ArgumentResolutionException(final Throwable cause) {
        super(cause);
    }
}
