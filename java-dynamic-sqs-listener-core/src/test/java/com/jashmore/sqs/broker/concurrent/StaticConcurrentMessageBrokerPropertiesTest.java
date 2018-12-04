package com.jashmore.sqs.broker.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
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
                .preferredConcurrencyPollingRateInMilliseconds(1)
                .concurrencyLevel(1)
                .build();

        // assert
        assertThat(retriever.getPreferredConcurrencyPollingRateInMilliseconds()).isEqualTo(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeConcurrencyPollingRatehrowsIllegalArgumentException() {
        // act
        StaticConcurrentMessageBrokerProperties
                .builder()
                .preferredConcurrencyPollingRateInMilliseconds(-1)
                .build();
    }
}
