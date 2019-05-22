package com.jashmore.sqs.util.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PropertyUtilsTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Test
    public void testSafelyGetLongValue() {
        assertThat(PropertyUtils.safelyGetLongValue("prop", () -> 1L, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetLongValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetLongValue("prop", () -> {
            throw new RuntimeException("Expected Test Exception");
        }, 5)).isEqualTo(5);
    }

    @Test
    public void testSafelyGetPositiveLongValue() {
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> 1L, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> -1L, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> 0L, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveLongValue("prop", () -> {
            throw new RuntimeException("Expected Test Exception");
        }, 5)).isEqualTo(5);
    }

    @Test
    public void testSafelyGetPositiveOrZeroLongValue() {
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> 1L, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> -1L, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> 0L, 5)).isEqualTo(0);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroLongValue("prop", () -> {
            throw new RuntimeException("Expected Test Exception");
        }, 5)).isEqualTo(5);
    }
    
    @Test
    public void testSafelyGetIntegerValue() {
        assertThat(PropertyUtils.safelyGetIntegerValue("prop", () -> 1, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetIntegerValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetIntegerValue("prop", () -> {
            throw new RuntimeException("Expected Test Exception");
        }, 5)).isEqualTo(5);
    }

    @Test
    public void testSafelyGetPositiveIntegerValue() {
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> 1, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> -1, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> 0, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveIntegerValue("prop", () -> {
            throw new RuntimeException("Expected Test Exception");
        }, 5)).isEqualTo(5);
    }

    @Test
    public void testSafelyGetPositiveOrZeroIntegerValue() {
        assertThat(PropertyUtils.safelyGetPositiveOrZeroIntegerValue("prop", () -> 1, 5)).isEqualTo(1);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroIntegerValue("prop", () -> -1, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroIntegerValue("prop", () -> 0, 5)).isEqualTo(0);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroIntegerValue("prop", () -> null, 5)).isEqualTo(5);
        assertThat(PropertyUtils.safelyGetPositiveOrZeroIntegerValue("prop", () -> {
            throw new RuntimeException("Expected Test Exception");
        }, 5)).isEqualTo(5);
    }
}