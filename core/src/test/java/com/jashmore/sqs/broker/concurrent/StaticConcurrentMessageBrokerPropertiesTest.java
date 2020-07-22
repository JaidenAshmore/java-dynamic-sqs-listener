package com.jashmore.sqs.broker.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class StaticConcurrentMessageBrokerPropertiesTest {
    @Test
    void concurrencyLevelReturnedFromConstructor() {
        // act
        final StaticConcurrentMessageBrokerProperties retriever = StaticConcurrentMessageBrokerProperties.builder()
                .concurrencyLevel(1)
                .build();

        // assert
        assertThat(retriever.getConcurrencyLevel()).isEqualTo(1);
    }

    @Test
    void negativeConcurrencyLevelThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(-1)
                        .build());
    }

    @Test
    void concurrencyPollingRateReturnedFromConstructor() {
        // act
        final StaticConcurrentMessageBrokerProperties retriever = StaticConcurrentMessageBrokerProperties.builder()
                .preferredConcurrencyPollingRate(Duration.ofMillis(1))
                .concurrencyLevel(1)
                .build();

        // assert
        assertThat(retriever.getConcurrencyPollingRate()).isEqualTo(Duration.ofMillis(1));
    }

    @Test
    void errorBackoffTimeReturnedFromConstructor() {
        // act
        final StaticConcurrentMessageBrokerProperties retriever = StaticConcurrentMessageBrokerProperties.builder()
                .preferredConcurrencyPollingRate(Duration.ofMillis(1))
                .concurrencyLevel(1)
                .errorBackoffTime(Duration.ofMillis(2))
                .build();

        // assert
        assertThat(retriever.getErrorBackoffTime()).isEqualTo(Duration.ofMillis(2));
    }
}
