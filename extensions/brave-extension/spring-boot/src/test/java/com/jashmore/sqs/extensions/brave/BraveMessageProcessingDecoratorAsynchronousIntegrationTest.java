package com.jashmore.sqs.extensions.brave;

import static org.assertj.core.api.Assertions.assertThat;

import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.test.TestSpanHandler;
import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.brave.propogation.SendMessageRemoteSetter;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.extensions.brave.spring.BraveMessageProcessingDecoratorConfiguration;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
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
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@SpringBootTest(
    classes = {
        BraveMessageProcessingDecoratorConfiguration.class,
        BraveMessageProcessingDecoratorAsynchronousIntegrationTest.TestConfig.class,
        QueueListenerConfiguration.class
    }
)
public class BraveMessageProcessingDecoratorAsynchronousIntegrationTest {

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
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
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
            @QueueListener(identifier = "asynchronous", value = QUEUE_NAME)
            public CompletableFuture<?> listenToMessageAsynchronously() {
                CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.create();
                return CompletableFuture.supplyAsync(
                    () -> {
                        final ScopedSpan scopedSpan = tracing.tracer().startScopedSpan("span-inside-async-code");
                        try {
                            Thread.sleep(500);
                            return null;
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Failed");
                        } finally {
                            scopedSpan.finish();
                        }
                    },
                    currentTraceContext.executorService(Executors.newCachedThreadPool())
                );
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
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        SendMessageRemoteSetter.create(tracing).inject(scopedSenderSpan.context(), messageAttributes);
        localSqsAsyncClient.sendMessage(
            QUEUE_NAME,
            SendMessageRequest.builder().messageBody("test").messageAttributes(messageAttributes).build()
        );

        // act
        assertThat(messageResolvedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        scopedSenderSpan.finish();

        // assert
        assertThat(spanHandler.spans()).hasSize(3);
        final MutableSpan internalProcessingSpan = spanHandler.get(0);
        final MutableSpan messageSpan = spanHandler.get(1);
        final MutableSpan senderSpan = spanHandler.get(2);
        // All spans are present
        assertThat(internalProcessingSpan.name()).isEqualTo("span-inside-async-code");
        assertThat(messageSpan.name()).isEqualTo("sqs-listener-asynchronous");
        assertThat(senderSpan.name()).isEqualTo("sender-span");

        // chain of parent IDs are correct
        assertThat(senderSpan.parentId()).isEqualTo(null);
        assertThat(messageSpan.parentId()).isEqualTo(senderSpan.id());
        assertThat(internalProcessingSpan.parentId()).isEqualTo(messageSpan.id());

        // all spans are for the same trace
        final String traceId = senderSpan.traceId();
        assertThat(messageSpan.traceId()).isEqualTo(traceId);
        assertThat(internalProcessingSpan.traceId()).isEqualTo(traceId);

        assertThat(internalProcessingSpan.parentId()).isEqualTo(messageSpan.id());
        assertThat(messageSpan.parentId()).isEqualTo(senderSpan.id());
        assertThat(senderSpan.parentId()).isEqualTo(null);
        assertThat(internalProcessingSpan.traceId()).isEqualTo(messageSpan.traceId());
        assertThat(messageSpan.traceId()).isEqualTo(senderSpan.traceId());
    }
}
