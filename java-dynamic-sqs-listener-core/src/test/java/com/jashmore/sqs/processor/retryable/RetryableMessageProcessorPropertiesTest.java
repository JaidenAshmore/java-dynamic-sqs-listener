package com.jashmore.sqs.processor.retryable;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class RetryableMessageProcessorPropertiesTest {
    @Test(expected = IllegalArgumentException.class)
    public void illegalArgumentExceptionThrownWhenRetryAttemptsLessThanZero() {
        // act
        RetryableMessageProcessorProperties
                .builder()
                .retryAttempts(-1)
                .retryDelayInMs(100)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalArgumentExceptionThrownWhenRetryDelay() {
        // act
        RetryableMessageProcessorProperties
                .builder()
                .retryAttempts(2)
                .retryDelayInMs(-100)
                .build();
    }

    @Test
    public void defaultsSetIfNoValuesSet() {
        // act
        final RetryableMessageProcessorProperties properties = RetryableMessageProcessorProperties
                .builder()
                .build();

        // assert
        assertThat(properties.getRetryAttempts()).isEqualTo(0);
        assertThat(properties.getRetryDelayInMs()).isEqualTo(0);
    }
}
