package com.jashmore.sqs.util;

/**
 * Exception that is expected and therefore would be nice to litter the logs with stack traces that make it seem like something is broken.
 */
public class ExpectedTestException extends RuntimeException {

    public ExpectedTestException() {
        super("This was expected");
    }

    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
