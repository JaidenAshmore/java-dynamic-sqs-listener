package com.jashmore.sqs.micronaut.container.fifo;

import com.jashmore.sqs.container.fifo.FifoMessageListenerContainerProperties;
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

class FifoQueueListenerParserTest {

    Environment mockEnvironment;

    FifoQueueListenerParser parser;

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
        parser = new FifoQueueListenerParser(mockEnvironment);
    }

    @Test
    void minimalAnnotation() throws Exception {
        // arrange
        final FifoQueueListener annotation = FifoQueueListenerParserTest.class.getMethod("method").getAnnotation(FifoQueueListener.class);

        // act
        final FifoMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(5);
        assertThat(properties.maximumMessagesInMessageGroup()).isEqualTo(2);
        assertThat(properties.maximumCachedMessageGroups()).isEqualTo(15);
        assertThat(properties.messageVisibilityTimeout()).isNull();
        assertThat(properties.tryAndProcessAnyExtraRetrievedMessagesOnShutdown()).isFalse();
        assertThat(properties.interruptThreadsProcessingMessagesOnShutdown()).isFalse();
    }

    @Test
    void primitiveValues() throws Exception {
        // arrange
        final FifoQueueListener annotation =
            FifoQueueListenerParserTest.class.getMethod("methodWithPrimitives").getAnnotation(FifoQueueListener.class);

        // act
        final FifoMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(7);
        assertThat(properties.maximumMessagesInMessageGroup()).isEqualTo(8);
        assertThat(properties.maximumCachedMessageGroups()).isEqualTo(10);
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(16));
        assertThat(properties.tryAndProcessAnyExtraRetrievedMessagesOnShutdown()).isTrue();
        assertThat(properties.interruptThreadsProcessingMessagesOnShutdown()).isTrue();
    }

    @Test
    void stringFieldsWithRawValues() throws Exception {
        // arrange
        final FifoQueueListener annotation =
            FifoQueueListenerParserTest.class.getMethod("stringMethod").getAnnotation(FifoQueueListener.class);

        // act
        final FifoMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(10);
        assertThat(properties.maximumMessagesInMessageGroup()).isEqualTo(6);
        assertThat(properties.maximumCachedMessageGroups()).isEqualTo(12);
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void stringFieldsWithReplacements() throws Exception {
        // arrange
        propertyMap.put("queue.concurrencyLevel", "2");
        propertyMap.put("queue.maximumMessagesInMessageGroup", "3");
        propertyMap.put("queue.maximumCachedMessageGroups", "15");
        propertyMap.put("queue.messageVisibilityInSeconds", "5");
        final FifoQueueListener annotation =
            FifoQueueListenerParserTest.class.getMethod("stringMethodWithReplacements").getAnnotation(FifoQueueListener.class);

        // act
        final FifoMessageListenerContainerProperties properties = parser.parse(annotation);

        // assert
        assertThat(properties.concurrencyLevel()).isEqualTo(2);
        assertThat(properties.maximumMessagesInMessageGroup()).isEqualTo(3);
        assertThat(properties.maximumCachedMessageGroups()).isEqualTo(15);
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @FifoQueueListener("queueName")
    public void method() {}

    @FifoQueueListener(
        value = "queueName",
        concurrencyLevel = 7,
        maximumMessagesInMessageGroup = 8,
        maximumCachedMessageGroups = 10,
        messageVisibilityTimeoutInSeconds = 16,
        interruptThreadsProcessingMessagesOnShutdown = true,
        tryAndProcessAnyExtraRetrievedMessagesOnShutdown = true
    )
    public void methodWithPrimitives() {}

    @FifoQueueListener(
        value = "queueName",
        concurrencyLevelString = "10",
        maximumMessagesInMessageGroupString = "6",
        maximumCachedMessageGroupsString = "12",
        messageVisibilityTimeoutInSecondsString = "15"
    )
    public void stringMethod() {}

    @FifoQueueListener(
        value = "queueName",
        concurrencyLevelString = "${queue.concurrencyLevel}",
        maximumMessagesInMessageGroupString = "${queue.maximumMessagesInMessageGroup}",
        maximumCachedMessageGroupsString = "${queue.maximumCachedMessageGroups}",
        messageVisibilityTimeoutInSecondsString = "${queue.messageVisibilityInSeconds}"
    )
    public void stringMethodWithReplacements() {}
}
