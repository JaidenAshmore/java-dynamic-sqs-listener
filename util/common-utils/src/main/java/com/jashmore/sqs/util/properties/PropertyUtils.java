package com.jashmore.sqs.util.properties;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Class that provides helper methods for dealing with the properties of the core components, e.g. by providing safety methods for getting default property
 * values when there is an error obtaining a value.
 */
@Slf4j
@UtilityClass
public class PropertyUtils {

    /**
     * Safely get a long value by returning a default value if there was an error getting the value or the value is null.
     *
     * @param propertyName  the name of the property obtaining the value from, this is used for log messages
     * @param valueSupplier the supplier that will provide the original value
     * @param defaultValue  the default value if the supplier throws an exception or is null
     * @return the long value for this property
     */
    public long safelyGetLongValue(final String propertyName, final Supplier<Long> valueSupplier, final long defaultValue) {
        return safelyGetValue(propertyName, valueSupplier, defaultValue, aLong -> true);
    }

    /**
     * Safely get a long value by returning a default value if there was an error getting the value, the value is null or the value is negative or zero.
     *
     * @param propertyName  the name of the property obtaining the value from, this is used for log messages
     * @param valueSupplier the supplier that will provide the original value
     * @param defaultValue  the default value if the supplier throws an exception or is null
     * @return the long value for this property
     */
    public long safelyGetPositiveLongValue(final String propertyName, final Supplier<Long> valueSupplier, final long defaultValue) {
        return safelyGetValue(propertyName, valueSupplier, defaultValue, aLong -> aLong > 0);
    }

    /**
     * Safely get a long value by returning a default value if there was an error getting the value, the value is null or the value is negative.
     *
     * @param propertyName  the name of the property obtaining the value from, this is used for log messages
     * @param valueSupplier the supplier that will provide the original value
     * @param defaultValue  the default value if the supplier throws an exception or is null
     * @return the long value for this property
     */
    public long safelyGetPositiveOrZeroLongValue(final String propertyName, final Supplier<Long> valueSupplier, final long defaultValue) {
        return safelyGetValue(propertyName, valueSupplier, defaultValue, aLong -> aLong >= 0);
    }

    /**
     * Safely get an int value by returning a default value if there was an error getting the value or the value is null.
     *
     * @param propertyName  the name of the property obtaining the value from, this is used for log messages
     * @param valueSupplier the supplier that will provide the original value
     * @param defaultValue  the default value if the supplier throws an exception or is null
     * @return the int value for this property
     */
    public int safelyGetIntegerValue(final String propertyName, final Supplier<Integer> valueSupplier, final int defaultValue) {
        return safelyGetValue(propertyName, valueSupplier, defaultValue, aInteger -> true);
    }

    /**
     * Safely get an int value by returning a default value if there was an error getting the value, the value is null or the value is negative or zero.
     *
     * @param propertyName  the name of the property obtaining the value from, this is used for log messages
     * @param valueSupplier the supplier that will provide the original value
     * @param defaultValue  the default value if the supplier throws an exception or is null
     * @return the int value for this property
     */
    public int safelyGetPositiveIntegerValue(final String propertyName, final Supplier<Integer> valueSupplier, final int defaultValue) {
        return safelyGetValue(propertyName, valueSupplier, defaultValue, aInteger -> aInteger > 0);
    }

    /**
     * Safely get an int value by returning a default value if there was an error getting the value, the value is null or the value is negative.
     *
     * @param propertyName  the name of the property obtaining the value from, this is used for log messages
     * @param valueSupplier the supplier that will provide the original value
     * @param defaultValue  the default value if the supplier throws an exception or is null
     * @return the int value for this property
     */
    public int safelyGetPositiveOrZeroIntegerValue(
        final String propertyName,
        final Supplier<Integer> valueSupplier,
        final int defaultValue
    ) {
        return safelyGetValue(propertyName, valueSupplier, defaultValue, aInteger -> aInteger >= 0);
    }

    /**
     * Get the duration from a property and if it is null or negative, return the default value supplied.
     *
     * @param propertyName  the name of the property
     * @param valueSupplier the supplier to get the property value
     * @param defaultValue  the default duration
     * @return the duration for the property
     */
    public Duration safelyGetPositiveOrZeroDuration(
        final String propertyName,
        final Supplier<Duration> valueSupplier,
        final Duration defaultValue
    ) {
        return safelyGetValue(propertyName, valueSupplier, defaultValue, duration -> !duration.isNegative());
    }

    private <T> T safelyGetValue(
        final String propertyName,
        final Supplier<T> valueSupplier,
        final T defaultValue,
        final Predicate<T> valueValidator
    ) {
        try {
            return Optional
                .ofNullable(valueSupplier.get())
                .filter(value -> {
                    if (!valueValidator.test(value)) {
                        log.error("Invalid value {} for property {} returning default value {}", value, propertyName, defaultValue);
                        return false;
                    }

                    return true;
                })
                .orElse(defaultValue);
        } catch (final RuntimeException throwable) {
            log.error("Error obtaining Property value {} returning default value {}", propertyName, defaultValue, throwable);
            return defaultValue;
        }
    }
}
