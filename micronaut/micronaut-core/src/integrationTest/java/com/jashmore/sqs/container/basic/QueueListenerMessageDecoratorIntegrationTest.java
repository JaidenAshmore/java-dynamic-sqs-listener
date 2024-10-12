package com.jashmore.sqs.container.basic;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@MicronautTest(environments = "QueueListenerMessageDecoratorIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class QueueListenerMessageDecoratorIntegrationTest {

    private static final String QUEUE_NAME = "QueueListenerMessageDecoratorIntegrationTest";
    private static final AtomicReference<String> mdcValue = new AtomicReference<>();
    private static final CountDownLatch MESSAGE_PROCESSED_LATCH = new CountDownLatch(1);

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "QueueListenerMessageDecoratorIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Singleton
        public MessageProcessingDecorator mdcDecorator() {
            return new MessageProcessingDecorator() {
                @Override
                public void onPreMessageProcessing(final MessageProcessingContext context, final Message message) {
                    MDC.put("test", "value");
                }
            };
        }

        @Singleton
        @Requires(env = "QueueListenerMessageDecoratorIntegrationTest")
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
