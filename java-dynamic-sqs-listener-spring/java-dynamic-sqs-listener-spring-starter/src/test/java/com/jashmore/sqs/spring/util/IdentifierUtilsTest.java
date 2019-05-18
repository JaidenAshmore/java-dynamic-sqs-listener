package com.jashmore.sqs.spring.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Method;

public class IdentifierUtilsTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Test
    public void identifierCanBeBuiltFromClassAndMethod() throws Exception {
        // arrange
        final Method method = IdentifierUtilsTest.class.getMethod("method");

        // act
        final String identifier = IdentifierUtils.buildIdentifierForMethod(IdentifierUtilsTest.class, method);

        // assert
        assertThat(identifier).isEqualTo("identifier-utils-test-method");
    }

    @SuppressWarnings("WeakerAccess")
    public void method() {

    }
}