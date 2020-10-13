package com.jashmore.sqs.decorator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.processor.DecoratingMessageProcessor;
import com.jashmore.sqs.processor.LambdaMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.util.collections.CollectionUtils;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;

@ExtendWith(MockitoExtension.class)
class AutoVisibilityExtenderMessageProcessingDecoratorTest {

    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder().queueUrl("url").build();

    @Mock
    SqsAsyncClient sqsAsyncClient;

    List<ChangeMessageVisibilityBatchRequest> changeVisibilityRequests;

    @BeforeEach
    void setUp() {
        changeVisibilityRequests = new ArrayList<>();
    }

    @Test
    void messageThatTakesLongerThanVisibilityTimeoutToProcessWillBeExtended() throws Exception {
        // arrange
        changingVisibilityIsSuccessful();
        final DecoratingMessageProcessor decoratingMessageProcessor = buildProcessor(
            new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {
                @Override
                public Duration visibilityTimeout() {
                    return Duration.ofSeconds(2);
                }

                @Override
                public Duration maxDuration() {
                    return Duration.ofSeconds(10);
                }

                @Override
                public Duration bufferDuration() {
                    return Duration.ofSeconds(1);
                }
            },
            new LambdaMessageProcessor(
                sqsAsyncClient,
                QUEUE_PROPERTIES,
                message -> {
                    try {
                        Thread.sleep(Duration.ofSeconds(1).plusMillis(200).toMillis());
                    } catch (InterruptedException interruptedException) {
                        throw new RuntimeException("Unexpected interruption");
                    }
                }
            )
        );

        // act
        final Message message = Message.builder().messageId("a").receiptHandle("aHandle").build();
        decoratingMessageProcessor.processMessage(message, () -> CompletableFuture.completedFuture(null)).get(5, TimeUnit.SECONDS);

        // assert
        verifyVisibilityChangedOnce(message);
    }

    @Test
    void messageThatTakesLongerMaximumDurationWillBeInterrupted() throws Exception {
        // arrange
        final DecoratingMessageProcessor decoratingMessageProcessor = buildProcessor(
            new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {
                @Override
                public Duration visibilityTimeout() {
                    return Duration.ofSeconds(99);
                }

                @Override
                public Duration maxDuration() {
                    return Duration.ofSeconds(2);
                }

                @Override
                public Duration bufferDuration() {
                    return Duration.ofSeconds(1);
                }
            },
            new LambdaMessageProcessor(
                sqsAsyncClient,
                QUEUE_PROPERTIES,
                message -> {
                    try {
                        Thread.sleep(Duration.ofSeconds(4).toMillis());
                        throw new RuntimeException("Should have been interrupted!");
                    } catch (InterruptedException interruptedException) {
                        // do nothing
                    }
                }
            )
        );

        // act
        final Message message = Message.builder().messageId("a").receiptHandle("aHandle").build();
        decoratingMessageProcessor.processMessage(message, () -> CompletableFuture.completedFuture(null)).get(5, TimeUnit.SECONDS);

        // assert
        verifyVisibilityNeverChanged(message);
    }

    @Test
    void multipleMessagesCanBeExtendedDuringProcessing() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        changingVisibilityIsSuccessful();
        final DecoratingMessageProcessor decoratingMessageProcessor = buildProcessor(
            new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {
                @Override
                public Duration visibilityTimeout() {
                    return Duration.ofSeconds(4);
                }

                @Override
                public Duration maxDuration() {
                    return Duration.ofSeconds(5);
                }

                @Override
                public Duration bufferDuration() {
                    return Duration.ofSeconds(1);
                }
            },
            new LambdaMessageProcessor(
                sqsAsyncClient,
                QUEUE_PROPERTIES,
                message -> {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                        throw new RuntimeException("Expected it to timeout");
                    } catch (InterruptedException interruptedException) {
                        // expected
                    }
                }
            )
        );
        final Message firstMessage = Message.builder().messageId("a").receiptHandle("aHandle").build();
        final Message secondMessage = Message.builder().messageId("b").receiptHandle("bHandle").build();
        final Message thirdMessage = Message.builder().messageId("c").receiptHandle("cHandle").build();

        // act
        final CompletableFuture<Void> firstMessageFuture = CompletableFuture.runAsync(
            () -> decoratingMessageProcessor.processMessage(firstMessage, () -> CompletableFuture.completedFuture(null))
        );
        Thread.sleep(1000);
        final CompletableFuture<Void> secondMessageFuture = CompletableFuture.runAsync(
            () -> decoratingMessageProcessor.processMessage(secondMessage, () -> CompletableFuture.completedFuture(null))
        );
        Thread.sleep(1000);
        final CompletableFuture<Void> thirdMessageFuture = CompletableFuture.runAsync(
            () -> decoratingMessageProcessor.processMessage(thirdMessage, () -> CompletableFuture.completedFuture(null))
        );
        final CompletableFuture<?> done = CompletableFutureUtils.allOf(
            CollectionUtils.immutableListOf(firstMessageFuture, secondMessageFuture, thirdMessageFuture)
        );

        // assert
        done.get(20, TimeUnit.SECONDS);
        verifyVisibilityChangedOnce(firstMessage);
        verifyVisibilityChangedOnce(secondMessage);
        verifyVisibilityChangedOnce(thirdMessage);
    }

    @Test
    void messageThatUsesAcknowledgeParameterWillNotExtendAfterAcknowledged() throws Exception {
        // arrange
        final DecoratingMessageProcessor decoratingMessageProcessor = buildProcessor(
            new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {
                @Override
                public Duration visibilityTimeout() {
                    return Duration.ofSeconds(1);
                }

                @Override
                public Duration maxDuration() {
                    return Duration.ofSeconds(10);
                }

                @Override
                public Duration bufferDuration() {
                    return Duration.ofMillis(500);
                }
            },
            new LambdaMessageProcessor(
                sqsAsyncClient,
                QUEUE_PROPERTIES,
                (message, acknowledge) -> {
                    acknowledge.acknowledgeSuccessful();
                    try {
                        Thread.sleep(Duration.ofMillis(1500).toMillis());
                    } catch (InterruptedException interruptedException) {
                        throw new RuntimeException("Unexpected interruption");
                    }
                }
            )
        );

        // act
        final Message message = Message.builder().messageId("a").receiptHandle("aHandle").build();
        decoratingMessageProcessor.processMessage(message, () -> CompletableFuture.completedFuture(null)).get(5, TimeUnit.SECONDS);

        // assert
        verifyVisibilityNeverChanged(message);
    }

    @Test
    void visibilityExtensionThatFailsWillKeepProcessing() throws Exception {
        // arrange
        changingVisibilityIsSuccessful();
        final DecoratingMessageProcessor decoratingMessageProcessor = buildProcessor(
            new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {
                @Override
                public Duration visibilityTimeout() {
                    return Duration.ofSeconds(2);
                }

                @Override
                public Duration maxDuration() {
                    return Duration.ofSeconds(10);
                }

                @Override
                public Duration bufferDuration() {
                    return Duration.ofSeconds(1);
                }
            },
            new LambdaMessageProcessor(
                sqsAsyncClient,
                QUEUE_PROPERTIES,
                message -> {
                    try {
                        Thread.sleep(Duration.ofSeconds(1).plusMillis(200).toMillis());
                    } catch (InterruptedException interruptedException) {
                        throw new RuntimeException("Unexpected interruption");
                    }
                }
            )
        );

        // act
        final Message message = Message.builder().messageId("a").receiptHandle("aHandle").build();
        decoratingMessageProcessor.processMessage(message, () -> CompletableFuture.completedFuture(null)).get(5, TimeUnit.SECONDS);

        // assert
        verifyVisibilityChangedOnce(message);
    }

    private DecoratingMessageProcessor buildProcessor(
        final AutoVisibilityExtenderMessageProcessingDecoratorProperties properties,
        final MessageProcessor delegate
    ) {
        final AutoVisibilityExtenderMessageProcessingDecorator decorator = new AutoVisibilityExtenderMessageProcessingDecorator(
            sqsAsyncClient,
            QUEUE_PROPERTIES,
            properties
        );
        return new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES, Collections.singletonList(decorator), delegate);
    }

    private void changingVisibilityIsSuccessful() {
        when(sqsAsyncClient.changeMessageVisibilityBatch(ArgumentMatchers.<Consumer<ChangeMessageVisibilityBatchRequest.Builder>>any()))
            .thenAnswer(
                invocation -> {
                    Consumer<ChangeMessageVisibilityBatchRequest.Builder> builder = invocation.getArgument(0);
                    final ChangeMessageVisibilityBatchRequest.Builder requestBuilder = ChangeMessageVisibilityBatchRequest.builder();
                    builder.accept(requestBuilder);
                    changeVisibilityRequests.add(requestBuilder.build());
                    return CompletableFuture.completedFuture(ChangeMessageVisibilityBatchResponse.builder().build());
                }
            );
    }

    private void verifyVisibilityNeverChanged(final Message message) {
        verifyVisibilityChanged(message, 0);
    }

    private void verifyVisibilityChangedOnce(final Message message) {
        verifyVisibilityChanged(message, 1);
    }

    private void verifyVisibilityChanged(final Message message, final int times) {
        final List<String> messagesWithVisibilityExtended = changeVisibilityRequests
            .stream()
            .flatMap(request -> request.entries().stream())
            .filter(entry -> entry.id().equals(message.messageId()))
            .map(ChangeMessageVisibilityBatchRequestEntry::id)
            .collect(Collectors.toList());
        assertThat(messagesWithVisibilityExtended).hasSize(times);
    }
}
