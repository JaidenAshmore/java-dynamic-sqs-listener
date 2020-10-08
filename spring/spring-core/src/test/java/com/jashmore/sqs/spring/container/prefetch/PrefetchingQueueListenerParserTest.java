package com.jashmore.sqs.spring.container.prefetch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainerProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PrefetchingQueueListenerParserTest {
    MockEnvironment mockEnvironment;

    PrefetchingQueueListenerParser parser;

    @BeforeEach
    void setUp() {
        mockEnvironment = new MockEnvironment();
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
        assertThat(properties.messageVisibilityTimeout()).isEqualTo(Duration.ofSeconds(30));
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
        mockEnvironment
            .withProperty("queue.concurrencyLevel", "2")
            .withProperty("queue.desiredMinPrefetchedMessages", "3")
            .withProperty("queue.maxPrefetchedMessages", "15")
            .withProperty("queue.messageVisibilityInSeconds", "5");
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
