package com.jashmore.sqs.broker.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StaticConcurrentMessageBrokerPropertiesTest {
    @Test
    void concurrencyLevelReturnedFromConstructor() {
        // act
        final StaticConcurrentMessageBrokerProperties retriever = StaticConcurrentMessageBrokerProperties
                .builder()
                .concurrencyLevel(1)
                .build();

        // assert
        assertThat(retriever.getConcurrencyLevel()).isEqualTo(1);
    }

    @Test
    void negativeConcurrencyLevelThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                StaticConcurrentMessageBrokerProperties
                        .builder()
                        .concurrencyLevel(-1)
                        .build());
    }

    @Test
    void concurrencyPollingLevelReturnedFromConstructor() {
        // act
        final StaticConcurrentMessageBrokerProperties retriever = StaticConcurrentMessageBrokerProperties
                .builder()
                .preferredConcurrencyPollingRateInMilliseconds(1L)
                .concurrencyLevel(1)
                .build();

        // assert
        assertThat(retriever.getConcurrencyPollingRateInMilliseconds()).isEqualTo(1);
    }

    @Test
    void negativeConcurrencyPollingRateThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> StaticConcurrentMessageBrokerProperties
                .builder()
                .preferredConcurrencyPollingRateInMilliseconds(-1L)
                .build());
    }
}
