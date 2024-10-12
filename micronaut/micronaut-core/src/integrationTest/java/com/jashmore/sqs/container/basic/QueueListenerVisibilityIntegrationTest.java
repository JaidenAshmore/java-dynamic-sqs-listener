package com.jashmore.sqs.container.basic;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@MicronautTest(environments = "QueueListenerVisibilityIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class QueueListenerVisibilityIntegrationTest {

    private static final String QUEUE_NAME = "QueueListenerVisibilityIntegrationTest";
    private static final int VISIBILITY_TIMEOUT_SECONDS = 2;

    private static final int NUMBER_OF_MESSAGES_TO_SEND = 1;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND + 1);

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "QueueListenerVisibilityIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(
                SqsQueuesConfig.QueueConfig
                    .builder()
                    .queueName(QUEUE_NAME)
                    .visibilityTimeout(VISIBILITY_TIMEOUT_SECONDS)
                    .maxReceiveCount(3)
                    .build()
            );
        }

        @Singleton
        @Requires(env = "QueueListenerVisibilityIntegrationTest")
        public static class MessageListener {

            @QueueListener(value = QUEUE_NAME)
            public void listenToMessage(@Payload final String payload) {
                log.info("Obtained message: {}", payload);
                COUNT_DOWN_LATCH.countDown();
                throw new ExpectedTestException();
            }
        }
    }

    @Test
    void noMessageVisibilityShouldDefaultToSqsVisibilityTimeout() throws InterruptedException {
        // arrange
        final long startTime = System.currentTimeMillis();

        // act
        localSqsAsyncClient.sendMessage(QUEUE_NAME, "message");
        assertThat(COUNT_DOWN_LATCH.await(20, TimeUnit.SECONDS)).isTrue();

        // assert
        final long endTime = System.currentTimeMillis();
        assertThat(endTime - startTime)
            .isGreaterThanOrEqualTo(VISIBILITY_TIMEOUT_SECONDS * 1000)
            .isLessThanOrEqualTo((2 + VISIBILITY_TIMEOUT_SECONDS) * 1000);
    }
}
