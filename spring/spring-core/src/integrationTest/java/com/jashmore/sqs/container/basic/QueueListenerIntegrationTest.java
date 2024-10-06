package com.jashmore.sqs.container.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Slf4j
@SpringBootTest(classes = { QueueListenerIntegrationTest.TestConfig.class, QueueListenerConfiguration.class })
class QueueListenerIntegrationTest {

    private static final String QUEUE_NAME = "QueueListenerIntegrationTest";

    private static final int NUMBER_OF_MESSAGES_TO_SEND = 100;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND);

    private static final Map<String, Boolean> messagesProcessed = new ConcurrentHashMap<>();

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

            @QueueListener(value = QUEUE_NAME)
            public void listenToMessage(@Payload final String payload) {
                log.info("Obtained message: {}", payload);
                messagesProcessed.put(payload, true);
                COUNT_DOWN_LATCH.countDown();
            }
        }
    }

    @Test
    void allMessagesAreProcessedByListeners() throws InterruptedException {
        // arrange
        IntStream
            .range(0, NUMBER_OF_MESSAGES_TO_SEND)
            .forEach(i -> {
                log.info("Sending message: " + i);
                localSqsAsyncClient.sendMessage(QUEUE_NAME, "message: " + i);
            });

        // act
        COUNT_DOWN_LATCH.await(20, TimeUnit.SECONDS);

        // assert
        assertThat(messagesProcessed).hasSize(100);
    }
}
