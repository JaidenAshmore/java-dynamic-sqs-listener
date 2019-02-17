package com.jashmore.sqs.broker.concurrent.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class StaticConcurrentMessageBrokerPropertiesTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Test
    public void equalityMatchesByValueNotObjectReference() {
        final StaticConcurrentMessageBrokerProperties properties = StaticConcurrentMessageBrokerProperties.builder()
                .concurrencyLevel(2)
                .preferredConcurrencyPollingRateInMilliseconds(1000)
                .build();


        final StaticConcurrentMessageBrokerProperties otherProperties = StaticConcurrentMessageBrokerProperties.builder()
                .concurrencyLevel(2)
                .preferredConcurrencyPollingRateInMilliseconds(1000)
                .build();

        assertThat(properties).isEqualTo(otherProperties);
    }

    @Test
    public void toStringContainsAllProperties() {
        final StaticConcurrentMessageBrokerProperties properties = StaticConcurrentMessageBrokerProperties.builder()
                .concurrencyLevel(2)
                .preferredConcurrencyPollingRateInMilliseconds(1000)
                .build();


        final StaticConcurrentMessageBrokerProperties otherProperties = StaticConcurrentMessageBrokerProperties.builder()
                .concurrencyLevel(2)
                .preferredConcurrencyPollingRateInMilliseconds(1000)
                .build();

        assertThat(properties.toString()).contains("concurrencyLevel");
        assertThat(properties.toString()).contains("2");
        assertThat(properties.toString()).contains("preferredConcurrencyPollingRateInMilliseconds");
        assertThat(properties.toString()).contains("1000");
    }
}