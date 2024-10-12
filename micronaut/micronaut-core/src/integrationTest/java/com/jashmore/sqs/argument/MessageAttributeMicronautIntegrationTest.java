package com.jashmore.sqs.argument;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.argument.attribute.MessageAttribute;
import com.jashmore.sqs.argument.attribute.MessageAttributeDataTypes;
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
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unused")
@Slf4j
@MicronautTest(environments = "MessageAttributeMicronautIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MessageAttributeMicronautIntegrationTest {

    private static final String QUEUE_NAME = "MessageAttributeSpringIntegrationTest";
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(1);
    private static final AtomicReference<String> messageAttributeReference = new AtomicReference<>();

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "MessageAttributeMicronautIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Singleton
        @Requires(env = "MessageAttributeMicronautIntegrationTest")
        public static class MessageListener {

            @QueueListener(value = QUEUE_NAME)
            public void listenToMessage(@MessageAttribute("key") final String value) {
                log.info("Obtained message: {}", value);
                messageAttributeReference.set(value);
                COUNT_DOWN_LATCH.countDown();
            }
        }
    }

    @Test
    public void allMessagesAreProcessedByListeners() throws Exception {
        // arrange
        localSqsAsyncClient
            .sendMessage(
                QUEUE_NAME,
                SendMessageRequest
                    .builder()
                    .messageBody("message")
                    .messageAttributes(
                        Collections.singletonMap(
                            "key",
                            MessageAttributeValue
                                .builder()
                                .dataType(MessageAttributeDataTypes.STRING.getValue())
                                .stringValue("value")
                                .build()
                        )
                    )
                    .build()
            )
            .get(5, TimeUnit.SECONDS);

        // act
        COUNT_DOWN_LATCH.await(20, TimeUnit.SECONDS);

        // assert
        assertThat(messageAttributeReference).hasValue("value");
    }
}
