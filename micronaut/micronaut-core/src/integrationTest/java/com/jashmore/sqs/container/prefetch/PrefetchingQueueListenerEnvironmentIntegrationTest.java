package com.jashmore.sqs.container.prefetch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.annotations.core.prefetch.PrefetchingQueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@Slf4j
@MicronautTest(environments = "PrefetchingQueueListenerEnvironmentIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrefetchingQueueListenerEnvironmentIntegrationTest {

    private static final String QUEUE_NAME = "PrefetchingQueueListenerIntegrationTest";
    private static final int NUMBER_OF_MESSAGES_TO_SEND = 100;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND);
    private static final int MESSAGE_VISIBILITY_IN_SECONDS = 2;

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "PrefetchingQueueListenerEnvironmentIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Singleton
        @Requires(env = "PrefetchingQueueListenerEnvironmentIntegrationTest")
        public static class MessageListener {

            @SuppressWarnings("unused")
            @PrefetchingQueueListener(value = QUEUE_NAME, messageVisibilityTimeoutInSeconds = MESSAGE_VISIBILITY_IN_SECONDS)
            public void listenToMessage(@Payload final String payload) {
                log.info("Obtained message: {}", payload);
                COUNT_DOWN_LATCH.countDown();
            }
        }
    }

    @Test
    void allMessagesAreProcessedByListeners() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        IntStream
            .range(0, NUMBER_OF_MESSAGES_TO_SEND)
            .forEach(index -> {
                log.info("Sending message: " + index);
                localSqsAsyncClient.sendMessage(QUEUE_NAME, "message: " + index);
            });

        // act
        // Wait the visibility timeout to make sure that all messages were processed and deleted from the queue
        Thread.sleep(MESSAGE_VISIBILITY_IN_SECONDS * 1000 * 2);

        // assert
        final int numberOfMessages = localSqsAsyncClient.getApproximateMessages(QUEUE_NAME).get(1, TimeUnit.SECONDS);
        assertThat(numberOfMessages).isEqualTo(0);
    }
}
