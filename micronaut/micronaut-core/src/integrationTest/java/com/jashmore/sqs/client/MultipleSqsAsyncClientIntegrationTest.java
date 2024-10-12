package com.jashmore.sqs.client;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@Slf4j
@MicronautTest(environments = "MultipleSqsAsyncClientIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultipleSqsAsyncClientIntegrationTest {

    private static final CyclicBarrier CYCLIC_BARRIER = new CyclicBarrier(3);

    @Inject
    @Named("firstClient")
    private LocalSqsAsyncClient firstClient;

    @Inject
    @Named("secondClient")
    private LocalSqsAsyncClient secondClient;

    @Factory
    @Requires(env = "MultipleSqsAsyncClientIntegrationTest")
    public static class TestConfig {

        @Singleton
        @Named("firstClient")
        public LocalSqsAsyncClient firstClient() {
            return new ElasticMqSqsAsyncClient("firstClientQueueName");
        }

        @Singleton
        @Named("secondClient")
        public LocalSqsAsyncClient secondClient() {
            return new ElasticMqSqsAsyncClient("secondClientQueueName");
        }

        @Singleton
        public SqsAsyncClientProvider sqsAsyncClientProvider(
            @Named("firstClient") SqsAsyncClient firstClient,
            @Named("secondClient") SqsAsyncClient secondClient
        ) {
            final Map<String, SqsAsyncClient> clients = new HashMap<>();
            clients.put("firstClient", firstClient);
            clients.put("secondClient", secondClient);
            return new DefaultSqsAsyncClientProvider(clients);
        }

        @Singleton
        @Requires(env = "MultipleSqsAsyncClientIntegrationTest")
        public static class MessageListeners {

            @QueueListener(value = "firstClientQueueName", sqsClient = "firstClient")
            public void firstClientMessageListener(final Message message) throws BrokenBarrierException, InterruptedException {
                log.info("Obtained first client message: {}", message);
                CYCLIC_BARRIER.await();
            }

            @QueueListener(value = "secondClientQueueName", sqsClient = "secondClient")
            public void secondClientMessageListener(final Message message) throws BrokenBarrierException, InterruptedException {
                log.info("Obtained second client message: {}", message);
                CYCLIC_BARRIER.await();
            }
        }
    }

    @Test
    void shouldBeAbleToProcessMessagesFromMultipleAwsAccounts() throws Exception {
        // arrange
        firstClient
            .sendMessage("firstClientQueueName", SendMessageRequest.builder().messageBody("message").build())
            .get(5, TimeUnit.SECONDS);
        secondClient
            .sendMessage("secondClientQueueName", SendMessageRequest.builder().messageBody("message").build())
            .get(5, TimeUnit.SECONDS);

        // act
        CYCLIC_BARRIER.await(20, TimeUnit.SECONDS);
    }
}
