package it.com.jashmore.sqs.container.basic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.basic.QueueListener;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
@SpringBootTest(classes = {Application.class, QueueListenerEnvironmentIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {
        "prop.concurrency=5"
})
public class QueueListenerEnvironmentIntegrationTest {
    private static final String QUEUE_NAME = "QueueListenerEnvironmentIntegrationTest";

    private static final int NUMBER_OF_MESSAGES_TO_SEND = 5;
    private static final CyclicBarrier CYCLIC_BARRIER = new CyclicBarrier(NUMBER_OF_MESSAGES_TO_SEND + 1);
    private static final AtomicInteger messagesProcessed = new AtomicInteger(0);

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
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
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
                } catch (final Exception e) {
                    // do nothing
                }
            }
        }
    }

    @Test
    public void allMessagesAreProcessedByListeners() throws Exception {
        // arrange
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND)
                .forEach(i -> localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "message: " + i));

        // act
        CYCLIC_BARRIER.await(10, TimeUnit.SECONDS);

        // assert
        assertThat(messagesProcessed.get()).isEqualTo(NUMBER_OF_MESSAGES_TO_SEND);
    }
}
