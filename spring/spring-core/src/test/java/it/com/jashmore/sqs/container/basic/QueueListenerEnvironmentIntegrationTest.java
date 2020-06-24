package it.com.jashmore.sqs.container.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
@SpringBootTest(classes = {QueueListenerEnvironmentIntegrationTest.TestConfig.class, QueueListenerConfiguration.class})
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {
        "prop.concurrency=5"
})
class QueueListenerEnvironmentIntegrationTest {
    private static final String QUEUE_NAME = "QueueListenerEnvironmentIntegrationTest";

    private static final int NUMBER_OF_MESSAGES_TO_SEND = 5;
    private static final CyclicBarrier CYCLIC_BARRIER = new CyclicBarrier(NUMBER_OF_MESSAGES_TO_SEND + 1);
    private static final AtomicInteger messagesProcessed = new AtomicInteger(0);

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @SuppressWarnings("unused")
        @Service
        public static class MessageListener {
            @QueueListener(value = QUEUE_NAME, concurrencyLevelString = "${prop.concurrency}")
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
    }

    @Test
    void allMessagesAreProcessedByListeners() throws Exception {
        // arrange
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND)
                .forEach(index -> {
                    final String messageBody = "message: " + index;
                    log.info("Sent message: {}", messageBody);
                    localSqsAsyncClient.sendMessage(QUEUE_NAME, messageBody);
                });

        // act
        CYCLIC_BARRIER.await(10, TimeUnit.SECONDS);

        // assert
        assertThat(messagesProcessed.get()).isEqualTo(NUMBER_OF_MESSAGES_TO_SEND);
    }
}
