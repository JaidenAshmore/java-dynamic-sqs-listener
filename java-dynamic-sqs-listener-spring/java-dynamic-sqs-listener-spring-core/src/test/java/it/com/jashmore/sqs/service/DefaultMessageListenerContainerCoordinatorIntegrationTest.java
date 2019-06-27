package it.com.jashmore.sqs.service;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.MessageListenerContainerCoordinator;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.test.LocalSqsRule;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import it.com.jashmore.example.Application;
import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest(classes = {Application.class, DefaultMessageListenerContainerCoordinatorIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
public class DefaultMessageListenerContainerCoordinatorIntegrationTest {
    private static final String QUEUE_NAME = "DefaultMessageListenerContainerCoordinatorIntegrationTest";
    private static final int MESSAGE_VISIBILITY_IN_SECONDS = 1;

    @ClassRule
    public static final LocalSqsRule LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_NAME).build()
    ));

    private static final CountDownLatch proxiedTestMethodCompleted = new CountDownLatch(1);

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
        }

        @Service
        public static class MessageListener {
            @SuppressWarnings("unused")
            @QueueListener(value = QUEUE_NAME, messageVisibilityTimeoutInSeconds = MESSAGE_VISIBILITY_IN_SECONDS)
            public void listenToMessage(@Payload final String payload) {
                log.info("Message received: {}", payload);
                proxiedTestMethodCompleted.countDown();
            }
        }
    }

    @Autowired
    private MessageListenerContainerCoordinator messageListenerContainerCoordinator;

    @Test
    public void queueContainerServiceCanStartAndStopQueuesForProcessing() throws Exception {
        // arrange
        messageListenerContainerCoordinator.stopAllContainers();
        log.info("Containers stopped");
        localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "message").get();

        // act
        Thread.sleep(1000); // Make sure the queues are not running
        messageListenerContainerCoordinator.startAllContainers();

        // assert
        proxiedTestMethodCompleted.await(30, TimeUnit.SECONDS);
    }
}
