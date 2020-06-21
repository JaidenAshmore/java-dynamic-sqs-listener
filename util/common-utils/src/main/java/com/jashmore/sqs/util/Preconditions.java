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
    public static void checkNotNull(Object value, String errorMessage) {
        if (value == null) {
            throw new NullPointerException(errorMessage);
        }
    }
}
