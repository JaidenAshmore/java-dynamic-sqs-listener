package it.com.jashmore.sqs.container.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
@SpringBootTest(classes = { QueueListenerMessageDecoratorIntegrationTest.TestConfig.class, QueueListenerConfiguration.class })
public class QueueListenerMessageDecoratorIntegrationTest {
    private static final String QUEUE_NAME = "QueueListenerMessageDecoratorIntegrationTest";
    private static final AtomicReference<String> mdcValue = new AtomicReference<>();
    private static final CountDownLatch MESSAGE_PROCESSED_LATCH = new CountDownLatch(1);

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Bean
        public MessageProcessingDecorator mdcDecorator() {
            return new MessageProcessingDecorator() {

                @Override
                public void onPreMessageProcessing(final MessageProcessingContext context, final Message message) {
                    MDC.put("test", "value");
                }
            };
        }

        @Service
        public static class MessageListener {

            @QueueListener(value = QUEUE_NAME)
            public void listenToMessage() {
                mdcValue.set(MDC.get("test"));
                MESSAGE_PROCESSED_LATCH.countDown();
            }
        }
    }

    @Test
    void messageProcessingBeansWillWrapMessageProcessing() throws Exception {
        // arrange
        localSqsAsyncClient.sendMessage(QUEUE_NAME, "message");

        // act
        assertThat(MESSAGE_PROCESSED_LATCH.await(5, TimeUnit.SECONDS)).isTrue();

        // assert
        assertThat(mdcValue).hasValue("value");
    }
}
