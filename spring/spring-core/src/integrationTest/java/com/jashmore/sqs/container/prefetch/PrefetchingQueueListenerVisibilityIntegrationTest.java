package com.jashmore.sqs.container.prefetch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.annotations.core.prefetch.PrefetchingQueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Slf4j
@SpringBootTest(classes = { PrefetchingQueueListenerVisibilityIntegrationTest.TestConfig.class, QueueListenerConfiguration.class })
public class PrefetchingQueueListenerVisibilityIntegrationTest {

    private static final String QUEUE_NAME = "PrefetchingQueueListenerVisibilityIntegrationTest";
    private static final int VISIBILITY_TIMEOUT_SECONDS = 2;

    private static final int NUMBER_OF_MESSAGES_TO_SEND = 1;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND + 1);

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {

        @Bean
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

        @Service
        public static class MessageListener {

            @PrefetchingQueueListener(value = QUEUE_NAME)
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
