package com.jashmore.sqs.util;

public class ExpectedTestException extends RuntimeException {
    public ExpectedTestException() {
        super("This was expected");
    }

    public synchronized Throwable fillInStackTrace()  {
        return this;
    }
}
