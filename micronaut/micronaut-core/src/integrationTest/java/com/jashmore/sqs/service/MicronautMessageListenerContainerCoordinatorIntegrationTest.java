package com.jashmore.sqs.service;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.container.MessageListenerContainerCoordinator;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
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
@MicronautTest(environments = "MicronautMessageListenerContainerCoordinatorIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MicronautMessageListenerContainerCoordinatorIntegrationTest {

    private static final String QUEUE_NAME = "DefaultMessageListenerContainerCoordinatorIntegrationTest";
    private static final int MESSAGE_VISIBILITY_IN_SECONDS = 1;

    private static final CountDownLatch proxiedTestMethodCompleted = new CountDownLatch(1);

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "MicronautMessageListenerContainerCoordinatorIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Singleton
        @Requires(env = "MicronautMessageListenerContainerCoordinatorIntegrationTest")
        public static class MessageListener {

            @SuppressWarnings("unused")
            @QueueListener(value = QUEUE_NAME, messageVisibilityTimeoutInSeconds = MESSAGE_VISIBILITY_IN_SECONDS)
            public void listenToMessage(@Payload final String payload) {
                log.info("Message received: {}", payload);
                proxiedTestMethodCompleted.countDown();
            }
        }
    }

    @Inject
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
