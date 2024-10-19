package com.jashmore.sqs.container.prefetch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.annotations.core.prefetch.PrefetchingQueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@Slf4j
@MicronautTest(environments = "PrefetchingQueueListenerIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "prop.concurrency", value = "5")
class PrefetchingQueueListenerIntegrationTest {

    private static final String QUEUE_NAME = "PrefetchingQueueListenerIntegrationTest";
    private static final int NUMBER_OF_MESSAGES_TO_SEND = 5;
    private static final CyclicBarrier CYCLIC_BARRIER = new CyclicBarrier(NUMBER_OF_MESSAGES_TO_SEND + 1);
    private static final AtomicInteger messagesProcessed = new AtomicInteger(0);

    private static final int MESSAGE_VISIBILITY_IN_SECONDS = 2;

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "PrefetchingQueueListenerIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Singleton
        @Requires(env = "PrefetchingQueueListenerIntegrationTest")
        @SuppressWarnings("unused")
        public static class MessageListener {

            @PrefetchingQueueListener(
                value = QUEUE_NAME,
                messageVisibilityTimeoutInSeconds = MESSAGE_VISIBILITY_IN_SECONDS,
                concurrencyLevelString = "${prop.concurrency}"
            )
            public void listenToMessage(@Payload final String payload) {
                try {
                    log.info("Received message: {}", payload);
                    messagesProcessed.incrementAndGet();
                    CYCLIC_BARRIER.await(10, TimeUnit.SECONDS);
                } catch (final Exception exception) {
                    // do nothing
                }
            }
        }
    }

    @Test
    void allMessagesAreProcessedByListeners() throws Exception {
        // arrange
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND).forEach(i -> localSqsAsyncClient.sendMessage(QUEUE_NAME, "message: " + i));

        // act
        CYCLIC_BARRIER.await(10, TimeUnit.SECONDS);

        // assert
        assertThat(messagesProcessed.get()).isEqualTo(NUMBER_OF_MESSAGES_TO_SEND);
    }
}
