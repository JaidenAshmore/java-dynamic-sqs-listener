package com.jashmore.sqs.container.basic;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@MicronautTest(environments = "QueueListenerIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueListenerIntegrationTest {

    private static final String QUEUE_NAME = "QueueListenerIntegrationTest";

    private static final int NUMBER_OF_MESSAGES_TO_SEND = 100;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND);

    private static final Map<String, Boolean> messagesProcessed = new ConcurrentHashMap<>();

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "QueueListenerIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Singleton
        @Requires(env = "QueueListenerIntegrationTest")
        public static class MessageListener {

            @QueueListener(value = QUEUE_NAME)
            public void listenToMessage(@Payload final String payload) {
                log.info("Obtained message: {}", payload);
                messagesProcessed.put(payload, true);
                COUNT_DOWN_LATCH.countDown();
            }
        }
    }

    @Test
    void allMessagesAreProcessedByListeners() throws InterruptedException {
        // arrange
        IntStream
            .range(0, NUMBER_OF_MESSAGES_TO_SEND)
            .forEach(i -> {
                log.info("Sending message: " + i);
                localSqsAsyncClient.sendMessage(QUEUE_NAME, "message: " + i);
            });

        // act
        COUNT_DOWN_LATCH.await(20, TimeUnit.SECONDS);

        // assert
        assertThat(messagesProcessed).hasSize(100);
    }
}
