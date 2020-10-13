package com.jashmore.sqs.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.MessageListenerContainerCoordinator;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Slf4j
@SpringBootTest(classes = { DefaultMessageListenerContainerCoordinatorIntegrationTest.TestConfig.class, QueueListenerConfiguration.class })
@ExtendWith(SpringExtension.class)
public class DefaultMessageListenerContainerCoordinatorIntegrationTest {

    private static final String QUEUE_NAME = "DefaultMessageListenerContainerCoordinatorIntegrationTest";
    private static final int MESSAGE_VISIBILITY_IN_SECONDS = 1;

    private static final CountDownLatch proxiedTestMethodCompleted = new CountDownLatch(1);

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
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
    void queueContainerServiceCanStartAndStopQueuesForProcessing() throws Exception {
        // arrange
        messageListenerContainerCoordinator.stopAllContainers();
        log.info("Containers stopped");
        localSqsAsyncClient.sendMessage(QUEUE_NAME, "message").get();

        // act
        Thread.sleep(200); // Make sure the queues are not running
        assertThat(proxiedTestMethodCompleted.getCount()).isEqualTo(1);
        messageListenerContainerCoordinator.startAllContainers();

        // assert
        proxiedTestMethodCompleted.await(30, TimeUnit.SECONDS);
    }
}
