package it.com.jashmore.sqs.container.prefetch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingQueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

@Slf4j
@SpringBootTest(classes = {PrefetchingQueueListenerEnvironmentIntegrationTest.TestConfig.class, QueueListenerConfiguration.class})
@ExtendWith(SpringExtension.class)
class PrefetchingQueueListenerEnvironmentIntegrationTest {
    private static final String QUEUE_NAME = "PrefetchingQueueListenerIntegrationTest";
    private static final int NUMBER_OF_MESSAGES_TO_SEND = 100;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND);
    private static final int MESSAGE_VISIBILITY_IN_SECONDS = 2;

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
            @PrefetchingQueueListener(value = QUEUE_NAME, messageVisibilityTimeoutInSeconds = MESSAGE_VISIBILITY_IN_SECONDS)
            public void listenToMessage(@Payload final String payload) {
                log.info("Obtained message: {}", payload);
                COUNT_DOWN_LATCH.countDown();
            }
        }
    }

    @Test
    void allMessagesAreProcessedByListeners() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND)
                .forEach(i -> {
                    log.info("Sending message: " + i);
                    localSqsAsyncClient.sendMessage(QUEUE_NAME, "message: " + i);
                });

        // act
        // Wait the visibility timeout to make sure that all messages were processed and deleted from the queue
        Thread.sleep(MESSAGE_VISIBILITY_IN_SECONDS * 1000 * 2);

        // assert
        final int numberOfMessages  = localSqsAsyncClient.getApproximateMessages(QUEUE_NAME).get(1, TimeUnit.SECONDS);
        assertThat(numberOfMessages).isEqualTo(0);
    }
}
