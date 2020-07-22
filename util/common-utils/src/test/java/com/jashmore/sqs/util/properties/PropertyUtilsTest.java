package com.jashmore.sqs.util.properties;

import static com.jashmore.sqs.util.properties.PropertyUtils.safelyGetPositiveOrZeroDuration;
import static com.jashmore.sqs.util.properties.PropertyUtils.safelyGetPositiveOrZeroIntegerValue;
import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.util.ExpectedTestException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class PropertyUtilsTest {

    @Test
    void testSafelyGetLongValue() {
        assertThat(PropertyUtils.safelyGetLongValue("prop", () -> 1L, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetLongValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetLongValue("prop", () -> {
            throw new ExpectedTestException();
        }, 5)).isEqualTo(5);
    }

    @Test
    void testSafelyGetPositiveLongValue() {
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> 1L, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> -1L, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> 0L, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> {
            throw new ExpectedTestException();
        }, 5)).isEqualTo(5);
    }

    @Test
    void testSafelyGetPositiveOrZeroLongValue() {
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> 1L, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> -1L, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> 0L, 5)).isEqualTo(0);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> {
            throw new ExpectedTestException();
        }, 5)).isEqualTo(5);
    }

    @Test
    void testSafelyGetIntegerValue() {
        assertThat(PropertyUtils.safelyGetIntegerValue("prop", () -> 1, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetIntegerValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetIntegerValue("prop", () -> {
            throw new ExpectedTestException();
        }, 5)).isEqualTo(5);
    }

    @Test
    void testSafelyGetPositiveIntegerValue() {
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> 1, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> -1, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> 0, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> {
            throw new ExpectedTestException();
        }, 5)).isEqualTo(5);
    }

    @Test
    void testSafelyGetPositiveOrZeroIntegerValue() {
        assertThat(safelyGetPositiveOrZeroIntegerValue("prop", () -> 1, 5)).isEqualTo(1);
        assertThat(safelyGetPositiveOrZeroIntegerValue("prop", () -> -1, 5)).isEqualTo(5);
        assertThat(safelyGetPositiveOrZeroIntegerValue("prop", () -> 0, 5)).isEqualTo(0);
        assertThat(safelyGetPositiveOrZeroIntegerValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(safelyGetPositiveOrZeroIntegerValue("prop", () -> {
            throw new ExpectedTestException();
        }, 5)).isEqualTo(5);
    }

    @Test
    void getPositiveOrZeroDuration() {
        final Duration defaultDuration = Duration.ofSeconds(5);
        assertThat(safelyGetPositiveOrZeroDuration("prop", () -> Duration.ofSeconds(1), defaultDuration)).isEqualTo(Duration.ofSeconds(1));
        assertThat(safelyGetPositiveOrZeroDuration("prop", () -> Duration.ofSeconds(-1), defaultDuration)).isEqualTo(defaultDuration);
        assertThat(safelyGetPositiveOrZeroDuration("prop", () -> Duration.ofSeconds(0), defaultDuration)).isEqualTo(Duration.ofSeconds(0));
        assertThat(safelyGetPositiveOrZeroDuration("prop", () -> null, defaultDuration)).isEqualTo(defaultDuration);
        assertThat(safelyGetPositiveOrZeroDuration("prop", () -> {
            throw new ExpectedTestException();
        }, defaultDuration)).isEqualTo(defaultDuration);
    }
}