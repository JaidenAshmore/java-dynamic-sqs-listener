package it.com.jashmore.sqs;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingQueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
@SpringBootTest(classes = {SqsSpringStarterIntegrationTest.TestConfig.class, QueueListenerConfiguration.class})
class SqsSpringStarterIntegrationTest {
    private static final String QUEUE_NAME = "SqsSpringStarterIntegrationTest";
    private static final int NUMBER_OF_MESSAGES_TO_SEND = 5;
    private static final CyclicBarrier CYCLIC_BARRIER = new CyclicBarrier(NUMBER_OF_MESSAGES_TO_SEND + 1);
    private static final AtomicInteger messagesProcessed = new AtomicInteger(0);
    private static final int MESSAGE_VISIBILITY_IN_SECONDS = 2;

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {
        @Service
        @SuppressWarnings("unused")
        public static class MessageListener {
            @PrefetchingQueueListener(value = QUEUE_NAME, messageVisibilityTimeoutInSeconds = MESSAGE_VISIBILITY_IN_SECONDS)
            public void listenToMessage(@Payload final String payload) {
                try {
                    log.info("Received message: {}", payload);
                    messagesProcessed.incrementAndGet();
                    CYCLIC_BARRIER.await(10, TimeUnit.SECONDS);
                } catch (final Exception exception) {
                    // do nothing
                }
            }
        }

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }
    }

    @Test
    void springStarterShouldAutomaticallySetUpSqsConfigurationIfIncluded() throws Exception {
        // arrange
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND)
                .forEach(i -> localSqsAsyncClient.sendMessage(QUEUE_NAME, "message: " + i));

        // act
        CYCLIC_BARRIER.await(10, TimeUnit.SECONDS);

        // assert
        assertThat(messagesProcessed.get()).isEqualTo(NUMBER_OF_MESSAGES_TO_SEND);
    }
}
