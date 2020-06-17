package it.com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@SpringBootTest(classes = {MessageArgumentResolutionSpringIntegrationTest.TestConfig.class, QueueListenerConfiguration.class})
@ExtendWith(SpringExtension.class)
public class MessageArgumentResolutionSpringIntegrationTest {
    private static final String QUEUE_NAME = "MessageArgumentResolutionSpringIntegrationTest";
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(1);
    private static final AtomicReference<Message> messageAttributeReference = new AtomicReference<>();

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
            @QueueListener(value = QUEUE_NAME)
            public void listenToMessage(final Message message) {
                log.info("Obtained message: {}", message);
                messageAttributeReference.set(message);
                COUNT_DOWN_LATCH.countDown();
            }
        }
    }

    @Test
    void allMessagesAreProcessedByListeners() throws Exception {
        // arrange
        localSqsAsyncClient.sendMessage(QUEUE_NAME, SendMessageRequest.builder()
                .messageBody("message")
                .build())
                .get(5, TimeUnit.SECONDS);

        // act
        COUNT_DOWN_LATCH.await(20, TimeUnit.SECONDS);

        // assert
        assertThat(messageAttributeReference.get().body()).isEqualTo("message");
    }
}
