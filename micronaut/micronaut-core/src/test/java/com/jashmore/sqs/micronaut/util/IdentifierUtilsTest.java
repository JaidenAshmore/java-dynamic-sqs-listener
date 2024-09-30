package com.jashmore.sqs.micronaut.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierUtilsTest {

    @Test
    void whenIdentifierEmptyCanBeBuiltFromClassAndMethod() throws Exception {
        // arrange
        final Method method = IdentifierUtilsTest.class.getMethod("method");

        // act
        final String identifier = IdentifierUtils.buildIdentifierForMethod("", IdentifierUtilsTest.class, method);

        // assert
        assertThat(identifier).isEqualTo("identifier-utils-test-method");
    }

    @Test
    void whenIdentifierNotEmptyStringItIsReturnedAfterBeingTrimmed() throws Exception {
        // arrange
        final Method method = IdentifierUtilsTest.class.getMethod("method");

        // act
        final String identifier = IdentifierUtils.buildIdentifierForMethod(" test ", IdentifierUtilsTest.class, method);

        // assert
        assertThat(identifier).isEqualTo("test");
    }

    @SuppressWarnings("WeakerAccess")
    public void method() {}
}
