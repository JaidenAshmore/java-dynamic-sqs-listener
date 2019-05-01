package it.com.jashmore.sqs.container.batching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.batching.BatchingQueueListener;
import com.jashmore.sqs.test.LocalSqsRule;
import com.jashmore.sqs.test.PurgeQueuesRule;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import it.com.jashmore.example.Application;
import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

@Slf4j
@SpringBootTest(classes = {Application.class, BatchingQueueListenerWrapperIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
public class BatchingQueueListenerWrapperIntegrationTest {
    private static final String QUEUE_NAME = "BatchingQueueListenerWrapperIntegrationTest";
    private static final int NUMBER_OF_MESSAGES_TO_SEND = 100;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND);
    private static final int MESSAGE_VISIBILITY_IN_SECONDS = 2;

    @ClassRule
    public static final LocalSqsRule LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_NAME).build()
    ));

    @Rule
    public final PurgeQueuesRule purgeQueuesRule = new PurgeQueuesRule(LOCAL_SQS_RULE.getLocalAmazonSqsAsync());

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {
        @Service
        public static class MessageListener {
            @SuppressWarnings("unused")
            @BatchingQueueListener(value = QUEUE_NAME, messageVisibilityTimeoutInSeconds = MESSAGE_VISIBILITY_IN_SECONDS)
            public void listenToMessage(@Payload final String payload) {
                log.info("Obtained message: {}", payload);
                COUNT_DOWN_LATCH.countDown();
            }
        }

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
        }
    }

    @Test
    public void allMessagesAreProcessedByListeners() throws InterruptedException, ExecutionException {
        // arrange
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND)
                .forEach(i -> {
                    log.info("Sending message: " + i);
                    localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "message: " + i);
                });

        // act
        COUNT_DOWN_LATCH.await();
        // Wait the visibility timeout to make sure that all messages were processed and deleted from the queue
        Thread.sleep(MESSAGE_VISIBILITY_IN_SECONDS * 1000 * 2);

        // assert
        final CompletableFuture<GetQueueAttributesResponse> queueAttributes = localSqsAsyncClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(localSqsAsyncClient.getQueueUrl(QUEUE_NAME))
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                .build());
        final GetQueueAttributesResponse getQueueAttributesResponse = queueAttributes.get();
        assertThat(getQueueAttributesResponse.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)).isEqualTo("0");
        assertThat(getQueueAttributesResponse.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).isEqualTo("0");
    }
}
