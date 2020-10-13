package com.jashmore.sqs.decorator.visibilityextender;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.spring.decorator.visibilityextender.AutoVisibilityExtender;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Slf4j
@SpringBootTest(
    classes = { AutoVisibilityExtenderMessageProcessingDecoratorFactoryIntegrationTest.TestConfig.class, QueueListenerConfiguration.class }
)
@TestPropertySource(
    properties = { "queue.visibilityTimeoutInSeconds=5", "queue.bufferDurationInSeconds=1", "queue.maxDurationInSeconds=10" }
)
@ExtendWith(SpringExtension.class)
public class AutoVisibilityExtenderMessageProcessingDecoratorFactoryIntegrationTest {

    private static final String QUEUE_NAME = "AutoVisibilityExtenderMessageProcessingDecoratorFactoryIntegrationTest";

    private static final CountDownLatch messageInterruptedLatch = new CountDownLatch(1);

    @Autowired
    LocalSqsAsyncClient localSqsAsyncClient;

    @SpringBootApplication
    @Configuration
    public static class TestConfig {

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Service
        public static class MessageListener {

            @QueueListener(value = QUEUE_NAME, messageVisibilityTimeoutInSecondsString = "${queue.visibilityTimeoutInSeconds}")
            @AutoVisibilityExtender(
                visibilityTimeoutInSecondsString = "${queue.visibilityTimeoutInSeconds}",
                maximumDurationInSecondsString = "${queue.maxDurationInSeconds}",
                bufferTimeInSecondsString = "${queue.bufferDurationInSeconds}"
            )
            public void listenToMessage(@Payload final String payload) {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    messageInterruptedLatch.countDown();
                }
            }
        }
    }

    @Test
    void classesThatAreProxiedShouldBeAbleToListenToMessagesWhenMethodsAndParametersAreAnnotated() throws Exception {
        // act
        final long startTime = System.currentTimeMillis();
        localSqsAsyncClient.sendMessage(QUEUE_NAME, "message");
        assertThat(messageInterruptedLatch.await(Duration.ofSeconds(12).getSeconds(), TimeUnit.SECONDS)).isTrue();

        // assert
        assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(Duration.ofSeconds(10).toMillis());
    }
}
