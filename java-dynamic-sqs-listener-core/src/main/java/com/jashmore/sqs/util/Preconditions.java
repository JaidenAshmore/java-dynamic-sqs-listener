package com.jashmore.sqs.util;

import lombok.experimental.UtilityClass;

/**
 * Utility class used for checking the arguments for a method are in the correct format.
 *
 * <p>This has been implemented again to reduce dependencies on other modules, more specifically Guava.
 */
@UtilityClass
public final class Preconditions {
    /**
     * Check whether the argument is null.
     *
     * @param argument     the argument to check is not null
     * @throws NullPointerException if the argument is null
     */
    public static void checkArgumentNotNull(final Object argument) throws NullPointerException {
        if (argument == null) {
            throw new NullPointerException();
        }
    }

    /**
     * Check whether the argument is null.
     *
     * @param argument     the argument to check is not null
     * @param argumentName the name of the argument to be used in the exception message
     * @throws NullPointerException if the argument is null
     */
    public static void checkArgumentNotNull(final Object argument, final String argumentName) throws NullPointerException {
        if (argument == null) {
            throw new NullPointerException(String.format("Argument with name '%s' should not be null", argumentName));
        }
    }

    /**
     * Check whether the argument is in a correct state.
     *
     * @param condition      check that the argument condition is true
     * @throws IllegalArgumentException if the condition is false
     */
    public static void checkArgument(final boolean condition) throws IllegalArgumentException {
        checkArgument(condition, null);
    }

    /**
     * Check whether the argument is in a correct state.
     *
     * @param condition      check that the argument condition is true
     * @param failureMessage the failure message that should be written when the condition fails
     * @throws IllegalArgumentException if the condition is false
     */
    public static void checkArgument(final boolean condition, final String failureMessage) throws IllegalArgumentException {
        if (!condition) {
            if (failureMessage == null) {
                throw new IllegalArgumentException();
            } else {
                throw new IllegalArgumentException(failureMessage);
            }
        }
    }
}
