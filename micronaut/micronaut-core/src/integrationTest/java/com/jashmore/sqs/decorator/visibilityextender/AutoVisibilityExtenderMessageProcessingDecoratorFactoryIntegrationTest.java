package com.jashmore.sqs.decorator.visibilityextender;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.annotations.decorator.visibilityextender.AutoVisibilityExtender;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@Slf4j
@MicronautTest(environments = "AutoVisibilityExtenderMessageProcessingDecoratorFactoryIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "queue.visibilityTimeoutInSeconds", value = "5")
@Property(name = "queue.visibilityTimeoutInSeconds", value = "5")
@Property(name = "queue.bufferDurationInSeconds", value = "1")
@Property(name = "queue.maxDurationInSeconds", value = "10")
public class AutoVisibilityExtenderMessageProcessingDecoratorFactoryIntegrationTest {

    private static final String QUEUE_NAME = "AutoVisibilityExtenderMessageProcessingDecoratorFactoryIntegrationTest";

    private static final CountDownLatch messageInterruptedLatch = new CountDownLatch(1);

    @Inject
    LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "AutoVisibilityExtenderMessageProcessingDecoratorFactoryIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Singleton
        @Requires(env = "AutoVisibilityExtenderMessageProcessingDecoratorFactoryIntegrationTest")
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
