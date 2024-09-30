package com.jashmore.sqs.micronaut.container.basic;

import com.jashmore.sqs.container.batching.BatchingMessageListenerContainerProperties;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueListenerParserTest {

    Environment mockEnvironment;

    QueueListenerParser parser;

    PropertyPlaceholderResolver propertyPlaceholderResolver;

    Map<String, Object> propertyMap;

    @BeforeEach
    void setUp() {
        mockEnvironment = mock(Environment.class);
        propertyMap = new HashMap<>();
        propertyPlaceholderResolver = new PropertyPlaceholderResolver() {
            @Override
            public Optional<String> resolvePlaceholders(String str) {
                if (str.startsWith("${")) {
                    return Optional.of(
                            propertyMap.get(str.replace("${", "").replace("}", ""))
                                    .toString());
                }
                return Optional.of(str);
            }

            @Override
            @SuppressWarnings("unchecked")
            public @NonNull <T> T resolveRequiredPlaceholder(String str, Class<T> type) throws ConfigurationException {
                return (T) propertyMap.get(str);
            }
        };
        when(mockEnvironment.getPlaceholderResolver()).thenReturn(propertyPlaceholderResolver);
        parser = new QueueListenerParser(mockEnvironment);
    }

    @Test
    void minimalAnnotation() throws Exception {
        // arrange
        final QueueListener annotation = QueueListenerParserTest.class.getMethod("method").getAnnotation(QueueListener.class);

        // act
        final BatchingMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(5);
        assertThat(properties.batchSize()).isEqualTo(5);
        assertThat(properties.getBatchingPeriod()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.messageVisibilityTimeout()).isNull();
        assertThat(properties.processAnyExtraRetrievedMessagesOnShutdown()).isTrue();
        assertThat(properties.interruptThreadsProcessingMessagesOnShutdown()).isFalse();
    }

    @Test
    void primitiveValues() throws Exception {
        // arrange
        final QueueListener annotation = QueueListenerParserTest.class.getMethod("methodWithPrimitives").getAnnotation(QueueListener.class);

        // act
        final BatchingMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(7);
        assertThat(properties.batchSize()).isEqualTo(8);
        assertThat(properties.getBatchingPeriod()).isEqualTo(Duration.ofMillis(1500));
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(16));
        assertThat(properties.processAnyExtraRetrievedMessagesOnShutdown()).isFalse();
        assertThat(properties.interruptThreadsProcessingMessagesOnShutdown()).isTrue();
    }

    @Test
    void stringFieldsWithRawValues() throws Exception {
        // arrange
        final QueueListener annotation = QueueListenerParserTest.class.getMethod("stringMethod").getAnnotation(QueueListener.class);

        // act
        final BatchingMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(10);
        assertThat(properties.batchSize()).isEqualTo(6);
        assertThat(properties.getBatchingPeriod()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.processAnyExtraRetrievedMessagesOnShutdown()).isTrue();
        assertThat(properties.interruptThreadsProcessingMessagesOnShutdown()).isFalse();
    }

    @Test
    void stringFieldsWithReplacements() throws Exception {
        // arrange
        propertyMap.put("queue.concurrencyLevel", "2");
        propertyMap.put("queue.batchSize", "3");
        propertyMap.put("queue.batchingPeriodInMs", "500");
        propertyMap.put("queue.messageVisibilityInSeconds", "5");
        final QueueListener annotation =
            QueueListenerParserTest.class.getMethod("stringMethodWithReplacements").getAnnotation(QueueListener.class);

        // act
        final BatchingMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(2);
        assertThat(properties.batchSize()).isEqualTo(3);
        assertThat(properties.getBatchingPeriod()).isEqualTo(Duration.ofMillis(500));
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.processAnyExtraRetrievedMessagesOnShutdown()).isTrue();
        assertThat(properties.interruptThreadsProcessingMessagesOnShutdown()).isFalse();
    }

    @QueueListener("queueName")
    public void method() {}

    @QueueListener(
        value = "queueName",
        concurrencyLevel = 7,
        batchSize = 8,
        batchingPeriodInMs = 1500,
        messageVisibilityTimeoutInSeconds = 16,
        interruptThreadsProcessingMessagesOnShutdown = true,
        processAnyExtraRetrievedMessagesOnShutdown = false
    )
    public void methodWithPrimitives() {}

    @QueueListener(
        value = "queueName",
        concurrencyLevelString = "10",
        batchSizeString = "6",
        batchingPeriodInMsString = "1000",
        messageVisibilityTimeoutInSecondsString = "15"
    )
    public void stringMethod() {}

    @QueueListener(
        value = "queueName",
        concurrencyLevelString = "${queue.concurrencyLevel}",
        batchSizeString = "${queue.batchSize}",
        batchingPeriodInMsString = "${queue.batchingPeriodInMs}",
        messageVisibilityTimeoutInSecondsString = "${queue.messageVisibilityInSeconds}"
    )
    public void stringMethodWithReplacements() {}
}
