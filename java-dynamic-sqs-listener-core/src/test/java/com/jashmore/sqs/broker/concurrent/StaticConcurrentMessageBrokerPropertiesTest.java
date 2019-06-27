package com.jashmore.sqs.broker.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import java.util.IllegalFormatException;

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
    public void noFieldsSetUsesDefaults() {
        // act
        final StaticConcurrentMessageBrokerProperties retriever = StaticConcurrentMessageBrokerProperties
                .builder()
                .build();

        // assert
        assertThat(retriever.getPreferredConcurrencyPollingRateInMilliseconds()).isEqualTo(60_000);
        assertThat(retriever.getConcurrencyLevel()).isEqualTo(0);
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
        assertThat(retriever.getPreferredConcurrencyPollingRateInMilliseconds()).isEqualTo(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeConcurrencyPollingRateThrowsIllegalArgumentException() {
        // act
        StaticConcurrentMessageBrokerProperties
                .builder()
                .preferredConcurrencyPollingRateInMilliseconds(-1L)
                .build();
    }

    @Test(expected = IllegalFormatException.class)
    public void invalidThreadNameFormatThrowsException() {
        // act
        StaticConcurrentMessageBrokerProperties
                .builder()
                .threadNameFormat("invalid-%s-format-%d")
                .build();
    }

    @Test
    public void shouldInterruptThreadsProcessingMessagesOnShutdownShouldBeFalseIfNothingSet() {
        // act
        final StaticConcurrentMessageBrokerProperties properties = StaticConcurrentMessageBrokerProperties.builder().build();

        // assert
        assertThat(properties.shouldInterruptThreadsProcessingMessagesOnShutdown()).isFalse();
    }

    @Test
    public void shouldInterruptThreadsProcessingMessagesOnShutdownShouldBeTrueIfSet() {
        // act
        final StaticConcurrentMessageBrokerProperties properties = StaticConcurrentMessageBrokerProperties.builder()
                .interruptThreadsProcessingMessagesOnShutdown(true)
                .build();

        // assert
        assertThat(properties.shouldInterruptThreadsProcessingMessagesOnShutdown()).isTrue();
    }
}
