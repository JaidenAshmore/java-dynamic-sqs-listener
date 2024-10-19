package com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
@MicronautTest(environments = "MessageArgumentResolutionMicronautIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MessageArgumentResolutionMicronautIntegrationTest {

    private static final String QUEUE_NAME = "MessageArgumentResolutionMicronautIntegrationTest";
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(1);
    private static final AtomicReference<Message> messageAttributeReference = new AtomicReference<>();

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "MessageArgumentResolutionMicronautIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Singleton
        @Requires(env = "MessageArgumentResolutionMicronautIntegrationTest")
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
        localSqsAsyncClient.sendMessage(QUEUE_NAME, SendMessageRequest.builder().messageBody("message").build()).get(5, TimeUnit.SECONDS);

        // act
        COUNT_DOWN_LATCH.await(20, TimeUnit.SECONDS);

        // assert
        assertThat(messageAttributeReference.get().body()).isEqualTo("message");
    }
}
