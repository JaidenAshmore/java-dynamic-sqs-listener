package com.jashmore.sqs.annotations.core.fifo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.container.fifo.FifoMessageListenerContainerProperties;
import com.jashmore.sqs.placeholder.StaticPlaceholderResolver;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FifoQueueListenerParserTest {

    StaticPlaceholderResolver placeholderResolver;

    FifoQueueListenerParser parser;

    @BeforeEach
    void setUp() {
        placeholderResolver = new StaticPlaceholderResolver();
        parser = new FifoQueueListenerParser(placeholderResolver);
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
        placeholderResolver
            .withMapping("${queue.concurrencyLevel}", "2")
            .withMapping("${queue.maximumMessagesInMessageGroup}", "3")
            .withMapping("${queue.maximumCachedMessageGroups}", "15")
            .withMapping("${queue.messageVisibilityInSeconds}", "5");
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
