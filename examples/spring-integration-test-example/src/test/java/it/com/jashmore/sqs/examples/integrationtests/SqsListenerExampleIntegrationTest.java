package it.com.jashmore.sqs.examples.integrationtests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.examples.integrationtests.IntegrationTestExampleApplication;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(classes = {
        IntegrationTestExampleApplication.class,
        SqsListenerExampleIntegrationTest.TestConfig.class
})
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {"sqs.queues.integrationTestingQueue=" + SqsListenerExampleIntegrationTest.QUEUE_NAME})
class SqsListenerExampleIntegrationTest {
    static final String QUEUE_NAME = "SqsListenerExampleIntegrationTest";
    private static final int QUEUE_MAX_RECEIVE_COUNT = 3;
    private static final int VISIBILITY_TIMEOUT_IN_SECONDS = 2;

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @MockBean
    private IntegrationTestExampleApplication.SomeService mockSomeService;

    @Configuration
    public static class TestConfig {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(Collections.singletonList(
                    SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_NAME)
                            .maxReceiveCount(QUEUE_MAX_RECEIVE_COUNT)
                            .visibilityTimeout(VISIBILITY_TIMEOUT_IN_SECONDS)
                            .build()));
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
        }).when(mockSomeService).run(anyString());

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
        }).when(mockSomeService).run(anyString());

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
        }).when(mockSomeService).run(anyString());

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
