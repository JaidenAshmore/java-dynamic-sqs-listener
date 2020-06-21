package com.jashmore.sqs.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Preconditions {
    /**
     * Make sure that the given value is not null, otherwise throw a {@link NullPointerException} with the provided error message.
     *
     * @param value        the value to check against
     * @param errorMessage the error message for the exception
     */
    public void checkNotNull(Object value, String errorMessage) {
        if (value == null) {
            throw new NullPointerException(errorMessage);
        }
    }

    /**
     * Make sure that the given value is not null, otherwise throw a {@link IllegalArgumentException} with the provided error message.
     *
     * @param value        the value to check against
     * @param errorMessage the error message for the exception
     */
    public void checkPositiveOrZero(int value, String errorMessage) {
        if (value < 0) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Make sure that the given expression is true, otherwise throw an {@link IllegalArgumentException} is thrown.
     *
     * @param value        the expression result to check
     * @param errorMessage the error message for the exception
     */
    public void checkArgument(boolean value, String errorMessage) {
        if (!value) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
