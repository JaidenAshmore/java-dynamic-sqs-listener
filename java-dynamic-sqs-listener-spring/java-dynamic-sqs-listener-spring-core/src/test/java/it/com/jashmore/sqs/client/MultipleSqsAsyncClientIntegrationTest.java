package it.com.jashmore.sqs.client;

import com.google.common.collect.ImmutableMap;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.client.DefaultSqsAsyncClientProvider;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
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
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@Slf4j
@SpringBootTest(classes = {MultipleSqsAsyncClientIntegrationTest.TestConfig.class, QueueListenerConfiguration.class})
@ExtendWith(SpringExtension.class)
class MultipleSqsAsyncClientIntegrationTest {
    private static final CyclicBarrier CYCLIC_BARRIER = new CyclicBarrier(3);

    @Autowired
    private LocalSqsAsyncClient firstClient;

    @Autowired
    private LocalSqsAsyncClient secondClient;

    @Configuration
    public static class TestConfig {
        @Bean
        public LocalSqsAsyncClient firstClient() {
            return new ElasticMqSqsAsyncClient("firstClientQueueName");
        }

        @Bean
        public LocalSqsAsyncClient secondClient() {
            return new ElasticMqSqsAsyncClient("secondClientQueueName");
        }

        @Bean
        public SqsAsyncClientProvider sqsAsyncClientProvider(SqsAsyncClient firstClient, SqsAsyncClient secondClient) {
            return new DefaultSqsAsyncClientProvider(ImmutableMap.of(
                    "firstClient", firstClient,
                    "secondClient", secondClient
            ));
        }

        @Service
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
        firstClient.sendMessage("firstClientQueueName", SendMessageRequest.builder()
                .messageBody("message")
                .build())
                .get(5, TimeUnit.SECONDS);
        secondClient.sendMessage("secondClientQueueName", SendMessageRequest.builder()
                .messageBody("message")
                .build())
                .get(5, TimeUnit.SECONDS);

        // act
        CYCLIC_BARRIER.await(20, TimeUnit.SECONDS);
    }
}
