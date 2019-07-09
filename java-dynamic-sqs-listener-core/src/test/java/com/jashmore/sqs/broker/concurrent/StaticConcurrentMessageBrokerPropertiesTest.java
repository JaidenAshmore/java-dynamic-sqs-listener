package com.jashmore.sqs.broker.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class StaticConcurrentMessageBrokerPropertiesTest {
    @Test
    public void concurrencyLevelReturnedFromConstructor() {
        // act
        final StaticConcurrentMessageBrokerProperties retriever = StaticConcurrentMessageBrokerProperties
                .builder()
                .concurrencyLevel(1)
                .build();

        // assert
        assertThat(retriever.getConcurrencyLevel()).isEqualTo(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeConcurrencyLevelThrowsIllegalArgumentException() {
        // act
        StaticConcurrentMessageBrokerProperties
                .builder()
                .concurrencyLevel(-1)
                .build();
    }

    @Test
    public void concurrencyPollingLevelReturnedFromConstructor() {
        // act
        final StaticConcurrentMessageBrokerProperties retriever = StaticConcurrentMessageBrokerProperties
                .builder()
                .preferredConcurrencyPollingRateInMilliseconds(1L)
                .concurrencyLevel(1)
                .build();

        // assert
        assertThat(retriever.getConcurrencyPollingRateInMilliseconds()).isEqualTo(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeConcurrencyPollingRateThrowsIllegalArgumentException() {
        // act
        StaticConcurrentMessageBrokerProperties
                .builder()
                .preferredConcurrencyPollingRateInMilliseconds(-1L)
                .build();
    }
}
