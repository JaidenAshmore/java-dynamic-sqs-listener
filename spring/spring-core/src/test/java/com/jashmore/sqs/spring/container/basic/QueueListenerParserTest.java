package com.jashmore.sqs.spring.container.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.container.batching.BatchingMessageListenerContainerProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class QueueListenerParserTest {

    MockEnvironment mockEnvironment;

    QueueListenerParser parser;

    @BeforeEach
    void setUp() {
        mockEnvironment = new MockEnvironment();
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
        mockEnvironment
            .withProperty("queue.concurrencyLevel", "2")
            .withProperty("queue.batchSize", "3")
            .withProperty("queue.batchingPeriodInMs", "500")
            .withProperty("queue.messageVisibilityInSeconds", "5");
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
