package it.com.jashmore.sqs.examples.integrationtests;

import static it.com.jashmore.sqs.examples.integrationtests.SqsListenerExampleIntegrationTest.QUEUE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.examples.integrationtests.IntegrationTestExampleApplication;
import com.jashmore.sqs.test.LocalSqsRule;
import com.jashmore.sqs.test.PurgeQueuesRule;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(classes = {
        IntegrationTestExampleApplication.class,
        SqsListenerExampleIntegrationTest.TestConfiguration.class
}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"sqs.queues.integrationTestingQueue=" + QUEUE_NAME})
public class SqsListenerExampleIntegrationTest {
    static final String QUEUE_NAME = "testQueue";
    private static final int QUEUE_MAX_RECEIVE_COUNT = 3;

    @ClassRule
    public static final LocalSqsRule LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_NAME).maxReceiveCount(QUEUE_MAX_RECEIVE_COUNT).visibilityTimeout(5).build()
    ));

    @Rule
    public final PurgeQueuesRule purgeQueuesRule = new PurgeQueuesRule(LOCAL_SQS_RULE.getLocalAmazonSqsAsync());

    @Configuration
    public static class TestConfiguration {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
        }
    }

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @MockBean
    private IntegrationTestExampleApplication.SomeService mockSomeService;

    @Test
    public void messagesPlacedOntoQueueArePickedUpMessageListener() throws Exception {
        // arrange
        final CountDownLatch messageReceivedCountDownLatch = new CountDownLatch(1);
        doAnswer(invocationOnMock -> {
            messageReceivedCountDownLatch.countDown();
            return null;
        }).when(mockSomeService).run(anyString());

        // act
        localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "my message");
        messageReceivedCountDownLatch.await(5, TimeUnit.SECONDS);

        // assert
        verify(mockSomeService).run("my message");
    }

    @Test
    public void messageFailingToProcessWillBeProcessedAgain() throws Exception {
        // arrange
        final CountDownLatch messageReceivedCountDownLatch = new CountDownLatch(1);
        final AtomicBoolean processedMessageOnce = new AtomicBoolean();
        doAnswer(invocationOnMock -> {
            if (!processedMessageOnce.getAndSet(true)) {
                throw new RuntimeException("error");
            }
            messageReceivedCountDownLatch.countDown();
            return null;
        }).when(mockSomeService).run(anyString());

        // act
        localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "my message");
        messageReceivedCountDownLatch.await(10, TimeUnit.SECONDS);

        // assert
        verify(mockSomeService, times(2)).run("my message");
    }

    @Test
    public void messageThatContinuesToFailWillBePlacedIntoDlq() throws Exception {
        // arrange
        final CountDownLatch messageReceivedCountDownLatch = new CountDownLatch(QUEUE_MAX_RECEIVE_COUNT);
        final String queueUrl = localSqsAsyncClient.getQueueUrl(QUEUE_NAME);
        doAnswer(invocationOnMock -> {
            messageReceivedCountDownLatch.countDown();
            throw new RuntimeException("error");
        }).when(mockSomeService).run(anyString());

        // act
        localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "my message");
        messageReceivedCountDownLatch.await(20, TimeUnit.SECONDS);
        waitForMessageVisibilityToExpire();

        // assert
        final GetQueueAttributesResponse queueAttributesResponse = localSqsAsyncClient.getQueueAttributes(builder -> builder
                .queueUrl(queueUrl + "-dlq")
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
        ).get();
        assertThat(queueAttributesResponse.attributes()).containsEntry(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "1");
    }

    private void waitForMessageVisibilityToExpire() throws InterruptedException {
        Thread.sleep(3000);
    }
}
