package it.com.jashmore.sqs.client;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.jashmore.sqs.spring.client.DefaultSqsAsyncClientProvider;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.test.LocalSqsRule;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import it.com.jashmore.example.Application;
import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@Slf4j
@SpringBootTest(classes = {Application.class, MultipleSqsAsyncClientIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
public class MultipleSqsAsyncClientIntegrationTest {
    private static final CyclicBarrier CYCLIC_BARRIER = new CyclicBarrier(3);

    @ClassRule
    public static final LocalSqsRule FIRST_CLIENT_LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName("firstClientQueueName").build()
    ));

    @ClassRule
    public static final LocalSqsRule SECOND_CLIENT_LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName("secondClientQueueName").build()
    ));

    @Configuration
    public static class TestConfig {
        @Bean
        public SqsAsyncClientProvider sqsAsyncClientProvider() {
            final LocalSqsAsyncClient firstClient = FIRST_CLIENT_LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
            firstClient.buildQueues();
            final LocalSqsAsyncClient secondClient = SECOND_CLIENT_LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
            secondClient.buildQueues();
            return new DefaultSqsAsyncClientProvider(ImmutableMap.of(
                    "firstClient", firstClient,
                    "secondClient", secondClient
            ));
        }

        @Service
        public static class MessageListeners {
            @QueueListener(value = "firstClientQueueName", sqsClient = "firstClient")
            public void firstClientMessageListener(final Message message) throws BrokenBarrierException, InterruptedException {
                log.info("Obtained first client message: {}", message);
                CYCLIC_BARRIER.await();
            }

            @QueueListener(value = "secondClientQueueName", sqsClient = "secondClient")
            public void secondClientMessageListener(final Message message) throws BrokenBarrierException, InterruptedException {
                log.info("Obtained second client message: {}", message);
                CYCLIC_BARRIER.await();
            }
        }
    }

    @Test
    public void shouldBeAbleToProcessMessagesFromMultipleAwsAccounts() throws Exception {
        // arrange
        FIRST_CLIENT_LOCAL_SQS_RULE.getLocalAmazonSqsAsync().sendMessageToLocalQueue("firstClientQueueName", SendMessageRequest.builder()
                .messageBody("message")
                .build())
                .get(5, TimeUnit.SECONDS);
        SECOND_CLIENT_LOCAL_SQS_RULE.getLocalAmazonSqsAsync().sendMessageToLocalQueue("secondClientQueueName", SendMessageRequest.builder()
                .messageBody("message")
                .build())
                .get(5, TimeUnit.SECONDS);

        // act
        CYCLIC_BARRIER.await(20, TimeUnit.SECONDS);
    }
}
