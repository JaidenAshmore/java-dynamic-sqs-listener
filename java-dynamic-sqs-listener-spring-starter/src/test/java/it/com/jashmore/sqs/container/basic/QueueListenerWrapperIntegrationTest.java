package it.com.jashmore.sqs.container.basic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.container.basic.QueueListener;
import it.com.jashmore.example.Application;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Slf4j
@SpringBootTest(classes = {Application.class, QueueListenerWrapperIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class QueueListenerWrapperIntegrationTest {
    private static final int NUMBER_OF_MESSAGES_TO_SEND = 100;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND);

    private static final Map<String, Boolean> messagesProcessed = new ConcurrentHashMap<>();

    @Autowired
    private AmazonSQSAsync amazonSqsAsync;

    @Configuration
    public static class TestConfig {
        @Bean
        public MessageListener messageListener() {
            return new MessageListener();
        }
    }

    @Test
    public void allMessagesAreProcessedByListeners() throws InterruptedException {
        // arrange
        final String queueUrl = amazonSqsAsync.getQueueUrl("QueueListenerWrapperIntegrationTest").getQueueUrl();
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND)
                .forEach(i -> {
                    log.info("Sending message: " + i);
                    amazonSqsAsync.sendMessageAsync(queueUrl, "message: " + i);
                });

        // act
        COUNT_DOWN_LATCH.await(20, TimeUnit.SECONDS);

        // assert
        assertThat(messagesProcessed).hasSize(100);
    }

    public static class MessageListener {
        @QueueListener(value = "QueueListenerWrapperIntegrationTest")
        public void listenToMessage(@Payload final String payload) {
            log.info("Obtained message: {}", payload);
            messagesProcessed.put(payload, true);
            COUNT_DOWN_LATCH.countDown();
        }
    }
}
