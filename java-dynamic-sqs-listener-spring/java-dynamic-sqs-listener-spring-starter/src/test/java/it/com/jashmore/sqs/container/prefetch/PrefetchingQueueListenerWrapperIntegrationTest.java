package it.com.jashmore.sqs.container.prefetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingQueueListener;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Slf4j
@SpringBootTest(classes = {Application.class, PrefetchingQueueListenerWrapperIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PrefetchingQueueListenerWrapperIntegrationTest {
    private static final String QUEUE_NAME = "PrefetchingQueueListenerWrapperIntegrationTest";

    private static final int NUMBER_OF_MESSAGES_TO_SEND = 100;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND);

    private static final Map<String, Boolean> messagesProcessed = new ConcurrentHashMap<>();

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
            @PrefetchingQueueListener(value = QUEUE_NAME)
            public void listenToMessage(@Payload final String payload) {
                log.info("Obtained message: {}", payload);
                messagesProcessed.put(payload, true);
                COUNT_DOWN_LATCH.countDown();
            }
        }

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
        }
    }

    @Test
    public void allMessagesAreProcessedByListeners() throws InterruptedException {
        // arrange
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND)
                .forEach(i -> {
                    log.info("Sending message: " + i);
                    localSqsAsyncClient.sendMessageToLocalQueue("PrefetchingQueueListenerWrapperIntegrationTest", "message: " + i);
                });

        // act
        COUNT_DOWN_LATCH.await(20, TimeUnit.SECONDS);

        // assert
        assertThat(messagesProcessed).hasSize(100);
    }
}
