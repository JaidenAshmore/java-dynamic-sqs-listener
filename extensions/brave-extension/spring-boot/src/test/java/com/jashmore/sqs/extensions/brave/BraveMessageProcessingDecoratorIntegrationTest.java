package com.jashmore.sqs.extensions.brave;

import static org.assertj.core.api.Assertions.assertThat;

import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.test.TestSpanHandler;
import com.jashmore.sqs.brave.SendMessageTracingExecutionInterceptor;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.extensions.brave.spring.BraveMessageProcessingDecoratorConfiguration;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;

@SpringBootTest(
    classes = {
        BraveMessageProcessingDecoratorConfiguration.class,
        BraveMessageProcessingDecoratorIntegrationTest.TestConfig.class,
        QueueListenerConfiguration.class
    }
)
public class BraveMessageProcessingDecoratorIntegrationTest {

    private static final String QUEUE_NAME = "BraveMessageProcessingDecoratorIntegrationTest";

    private static final TestSpanHandler spanHandler = new TestSpanHandler();
    private static CountDownLatch messageResolvedLatch = new CountDownLatch(1);

    @Autowired
    private Tracing tracing;

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient(final Tracing tracing) {
            return new ElasticMqSqsAsyncClient(
                QUEUE_NAME,
                clientBuilder ->
                    clientBuilder.overrideConfiguration(
                        overrideBuilder -> overrideBuilder.addExecutionInterceptor(new SendMessageTracingExecutionInterceptor(tracing))
                    )
            );
        }

        @Bean
        public MessageProcessingDecorator messageFinishedDecorator() {
            return new MessageProcessingDecorator() {
                @Override
                public void onMessageResolvedSuccess(final MessageProcessingContext context, final Message message) {
                    messageResolvedLatch.countDown();
                }
            };
        }

        @Bean
        public Tracing tracing() {
            return Tracing.newBuilder().addSpanHandler(spanHandler).build();
        }

        @Service
        public static class MessageListener {

            private final Tracing tracing;

            public MessageListener(final Tracing tracing) {
                this.tracing = tracing;
            }

            @SuppressWarnings("unused")
            @QueueListener(identifier = "my-identifier", value = QUEUE_NAME)
            public void listenToMessage() {
                tracing.tracer().startScopedSpan("message-processing-internal-span").finish();
            }
        }
    }

    @BeforeEach
    void setUpMessageCountDownLatch() {
        messageResolvedLatch = new CountDownLatch(1);
    }

    @AfterEach
    void clearSpanHandler() throws InterruptedException, ExecutionException, TimeoutException {
        localSqsAsyncClient.purgeAllQueues().get(5, TimeUnit.SECONDS);
        spanHandler.clear();
    }

    @AfterEach
    public void tearDownTracing() {
        tracing.close();
    }

    @Test
    void allTheSpanInformationIsCorrectlyJoinedTogether() throws Exception {
        // arrange
        final ScopedSpan scopedSenderSpan = tracing.tracer().startScopedSpan("sender-span");
        final GetQueueUrlResponse queueUrlResponse = localSqsAsyncClient
            .getQueueUrl(builder -> builder.queueName(QUEUE_NAME))
            .get(5, TimeUnit.SECONDS);
        localSqsAsyncClient
            .sendMessage(builder -> builder.queueUrl(queueUrlResponse.queueUrl()).messageBody("test"))
            .get(5, TimeUnit.SECONDS);

        // act
        assertThat(messageResolvedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(500); // make sure that everything else is completed
        scopedSenderSpan.finish();

        // assert
        assertThat(spanHandler.spans()).hasSize(4);
        final MutableSpan sendMessageSpan = spanHandler.get(0);
        final MutableSpan internalProcessingSpan = spanHandler.get(1);
        final MutableSpan messageSpan = spanHandler.get(2);
        final MutableSpan senderSpan = spanHandler.get(3);

        // All spans are present
        assertThat(sendMessageSpan.name()).isEqualTo("sqs-send-message");
        assertThat(internalProcessingSpan.name()).isEqualTo("message-processing-internal-span");
        assertThat(messageSpan.name()).isEqualTo("sqs-listener-my-identifier");
        assertThat(senderSpan.name()).isEqualTo("sender-span");

        // chain of parent IDs are correct
        assertThat(senderSpan.parentId()).isEqualTo(null);
        assertThat(sendMessageSpan.parentId()).isEqualTo(senderSpan.id());
        assertThat(messageSpan.parentId()).isEqualTo(sendMessageSpan.id());
        assertThat(internalProcessingSpan.parentId()).isEqualTo(messageSpan.id());

        // all spans are for the same trace
        final String traceId = senderSpan.traceId();
        assertThat(sendMessageSpan.traceId()).isEqualTo(traceId);
        assertThat(messageSpan.traceId()).isEqualTo(traceId);
        assertThat(internalProcessingSpan.traceId()).isEqualTo(traceId);
    }
}
