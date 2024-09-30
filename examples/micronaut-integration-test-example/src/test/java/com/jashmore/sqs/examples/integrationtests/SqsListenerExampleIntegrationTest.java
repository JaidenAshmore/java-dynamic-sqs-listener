package com.jashmore.sqs.examples.integrationtests;

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "sqs.queues.integrationTestingQueue", value = SqsListenerExampleIntegrationTest.QUEUE_NAME)
class SqsListenerExampleIntegrationTest {

    static final String QUEUE_NAME = "SqsListenerExampleIntegrationTest";
    private static final int QUEUE_MAX_RECEIVE_COUNT = 3;
    private static final int VISIBILITY_TIMEOUT_IN_SECONDS = 2;

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @MockBean(TestApplication.SomeService.class)
    TestApplication.SomeService someService() {
        return mock(TestApplication.SomeService.class);
    }

    @Inject
    private TestApplication.SomeService mockSomeService;

    @Factory
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(
                Collections.singletonList(
                    SqsQueuesConfig.QueueConfig
                        .builder()
                        .queueName(QUEUE_NAME)
                        .maxReceiveCount(QUEUE_MAX_RECEIVE_COUNT)
                        .visibilityTimeout(VISIBILITY_TIMEOUT_IN_SECONDS)
                        .build()
                )
            );
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException, ExecutionException, TimeoutException {
        localSqsAsyncClient.purgeAllQueues().get(5, TimeUnit.SECONDS);
    }

    @Test
    void messagesPlacedOntoQueueArePickedUpMessageListener() throws Exception {
        // arrange
        final CountDownLatch messageReceivedCountDownLatch = new CountDownLatch(1);
        doAnswer(invocationOnMock -> {
                messageReceivedCountDownLatch.countDown();
                return null;
            })
            .when(mockSomeService)
            .run(anyString());

        // act
        localSqsAsyncClient.sendMessage(QUEUE_NAME, "my message");
        messageReceivedCountDownLatch.await(VISIBILITY_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

        // assert
        verify(mockSomeService).run("my message");
    }

    @Test
    void messageFailingToProcessWillBeProcessedAgain() throws Exception {
        // arrange
        final CountDownLatch messageReceivedCountDownLatch = new CountDownLatch(1);
        final AtomicBoolean processedMessageOnce = new AtomicBoolean();
        doAnswer(invocationOnMock -> {
                if (!processedMessageOnce.getAndSet(true)) {
                    throw new ExpectedTestException();
                }
                messageReceivedCountDownLatch.countDown();
                return null;
            })
            .when(mockSomeService)
            .run(anyString());

        // act
        localSqsAsyncClient.sendMessage(QUEUE_NAME, "my message");
        messageReceivedCountDownLatch.await(3 * VISIBILITY_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

        // assert
        verify(mockSomeService, times(2)).run("my message");
    }

    @Test
    void messageThatContinuesToFailWillBePlacedIntoDlq() throws Exception {
        // arrange
        final CountDownLatch messageReceivedCountDownLatch = new CountDownLatch(QUEUE_MAX_RECEIVE_COUNT);
        doAnswer(invocationOnMock -> {
                messageReceivedCountDownLatch.countDown();
                throw new ExpectedTestException();
            })
            .when(mockSomeService)
            .run(anyString());

        // act
        localSqsAsyncClient.sendMessage(QUEUE_NAME, "my message");
        messageReceivedCountDownLatch.await(VISIBILITY_TIMEOUT_IN_SECONDS * (QUEUE_MAX_RECEIVE_COUNT + 1), TimeUnit.SECONDS);
        waitForMessageVisibilityToExpire();

        // assert
        final int numberOfMessages = localSqsAsyncClient.getApproximateMessages(QUEUE_NAME + "-dlq").get();
        assertThat(numberOfMessages).isEqualTo(1);
    }

    private void waitForMessageVisibilityToExpire() throws InterruptedException {
        Thread.sleep(Duration.ofSeconds(VISIBILITY_TIMEOUT_IN_SECONDS + 1).toMillis());
    }
}
