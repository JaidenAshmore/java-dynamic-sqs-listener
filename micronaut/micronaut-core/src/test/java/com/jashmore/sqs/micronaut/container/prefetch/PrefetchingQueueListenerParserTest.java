package com.jashmore.sqs.micronaut.container.prefetch;

import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainerProperties;
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

class PrefetchingQueueListenerParserTest {

    Environment mockEnvironment;

    PrefetchingQueueListenerParser parser;

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
        parser = new PrefetchingQueueListenerParser(mockEnvironment);
    }

    @Test
    void minimalAnnotation() throws Exception {
        // arrange
        final PrefetchingQueueListener annotation =
            PrefetchingQueueListenerParserTest.class.getMethod("method").getAnnotation(PrefetchingQueueListener.class);

        // act
        final PrefetchingMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(5);
        assertThat(properties.desiredMinPrefetchedMessages()).isEqualTo(1);
        assertThat(properties.maxPrefetchedMessages()).isEqualTo(10);
        assertThat(properties.messageVisibilityTimeout()).isNull();
        assertThat(properties.processAnyExtraRetrievedMessagesOnShutdown()).isTrue();
        assertThat(properties.interruptThreadsProcessingMessagesOnShutdown()).isFalse();
    }

    @Test
    void primitiveValues() throws Exception {
        // arrange
        final PrefetchingQueueListener annotation =
            PrefetchingQueueListenerParserTest.class.getMethod("methodWithPrimitives").getAnnotation(PrefetchingQueueListener.class);

        // act
        final PrefetchingMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(7);
        assertThat(properties.desiredMinPrefetchedMessages()).isEqualTo(8);
        assertThat(properties.maxPrefetchedMessages()).isEqualTo(15);
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(16));
        assertThat(properties.processAnyExtraRetrievedMessagesOnShutdown()).isFalse();
        assertThat(properties.interruptThreadsProcessingMessagesOnShutdown()).isTrue();
    }

    @Test
    void stringFieldsWithRawValues() throws Exception {
        // arrange
        final PrefetchingQueueListener annotation =
            PrefetchingQueueListenerParserTest.class.getMethod("stringMethod").getAnnotation(PrefetchingQueueListener.class);

        // act
        final PrefetchingMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(10);
        assertThat(properties.desiredMinPrefetchedMessages()).isEqualTo(6);
        assertThat(properties.maxPrefetchedMessages()).isEqualTo(12);
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void stringFieldsWithReplacements() throws Exception {
        // arrange
        propertyMap.put("queue.concurrencyLevel", "2");
        propertyMap.put("queue.desiredMinPrefetchedMessages", "3");
        propertyMap.put("queue.maxPrefetchedMessages", "15");
        propertyMap.put("queue.messageVisibilityInSeconds", "5");
        final PrefetchingQueueListener annotation =
            PrefetchingQueueListenerParserTest.class.getMethod("stringMethodWithReplacements")
                .getAnnotation(PrefetchingQueueListener.class);

        // act
        final PrefetchingMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(2);
        assertThat(properties.desiredMinPrefetchedMessages()).isEqualTo(3);
        assertThat(properties.maxPrefetchedMessages()).isEqualTo(15);
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @PrefetchingQueueListener("queueName")
    public void method() {}

    @PrefetchingQueueListener(
        value = "queueName",
        concurrencyLevel = 7,
        desiredMinPrefetchedMessages = 8,
        maxPrefetchedMessages = 15,
        messageVisibilityTimeoutInSeconds = 16,
        interruptThreadsProcessingMessagesOnShutdown = true,
        processAnyExtraRetrievedMessagesOnShutdown = false
    )
    public void methodWithPrimitives() {}

    @PrefetchingQueueListener(
        value = "queueName",
        concurrencyLevelString = "10",
        desiredMinPrefetchedMessagesString = "6",
        maxPrefetchedMessagesString = "12",
        messageVisibilityTimeoutInSecondsString = "15"
    )
    public void stringMethod() {}

    @PrefetchingQueueListener(
        value = "queueName",
        concurrencyLevelString = "${queue.concurrencyLevel}",
        desiredMinPrefetchedMessagesString = "${queue.desiredMinPrefetchedMessages}",
        maxPrefetchedMessagesString = "${queue.maxPrefetchedMessages}",
        messageVisibilityTimeoutInSecondsString = "${queue.messageVisibilityInSeconds}"
    )
    public void stringMethodWithReplacements() {}
}
