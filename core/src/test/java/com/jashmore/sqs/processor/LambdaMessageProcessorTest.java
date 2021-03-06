package com.jashmore.sqs.processor;

import static com.jashmore.sqs.processor.argument.VisibilityExtender.DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.Message;

@ExtendWith(MockitoExtension.class)
class LambdaMessageProcessorTest {

    private static final QueueProperties queueProperties = QueueProperties.builder().queueUrl("url").build();
    private static final Message message = Message.builder().receiptHandle("handle").build();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private Supplier<CompletableFuture<?>> resolveMessage;

    @Nested
    class OnlyConsumeMessage {

        @Test
        void successfulExecutionWillResolveFuture() {
            // arrange
            when(resolveMessage.get()).thenReturn(CompletableFuture.completedFuture(null));
            final LambdaMessageProcessor processor = new LambdaMessageProcessor(sqsAsyncClient, queueProperties, message -> {});

            // act
            final CompletableFuture<?> result = processor.processMessage(message, resolveMessage);

            // assert
            assertThat(result).isCompleted();
            verify(resolveMessage).get();
        }

        @Test
        void failureToProcessMessageWillRejectFuture() {
            // arrange
            final LambdaMessageProcessor processor = new LambdaMessageProcessor(
                sqsAsyncClient,
                queueProperties,
                message -> {
                    throw new ExpectedTestException();
                }
            );

            // act
            final CompletableFuture<?> result = processor.processMessage(message, resolveMessage);

            // assert
            assertThat(result).isCompletedExceptionally();
            verify(resolveMessage, never()).get();
        }
    }

    @Nested
    class ConsumeMessageWithVisibility {

        @Test
        void visibilityExtenderCanBeUsedToExtendMessageVisibility() {
            // arrange
            when(resolveMessage.get()).thenReturn(CompletableFuture.completedFuture(null));
            final LambdaMessageProcessor processor = new LambdaMessageProcessor(
                sqsAsyncClient,
                queueProperties,
                false,
                (message, visibilityExtender) -> visibilityExtender.extend()
            );

            // act
            final CompletableFuture<?> result = processor.processMessage(message, resolveMessage);

            // assert
            assertThat(result).isCompleted();
            verify(sqsAsyncClient)
                .changeMessageVisibility(
                    ChangeMessageVisibilityRequest
                        .builder()
                        .visibilityTimeout(DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS)
                        .queueUrl("url")
                        .receiptHandle("handle")
                        .build()
                );
        }
    }

    @Nested
    class ConsumeMessageWithAcknowledge {

        @Test
        void successfulExecutionWillResolveFutureButNotResolveMessage() {
            // arrange
            final LambdaMessageProcessor processor = new LambdaMessageProcessor(
                sqsAsyncClient,
                queueProperties,
                (message, acknowledge) -> {}
            );

            // act
            final CompletableFuture<?> result = processor.processMessage(message, resolveMessage);

            // assert
            assertThat(result).isCompleted();
            verify(resolveMessage, never()).get();
        }

        @Test
        void failureExecutionWillRejectFutureAndNotResolveMessage() {
            // arrange
            final LambdaMessageProcessor processor = new LambdaMessageProcessor(
                sqsAsyncClient,
                queueProperties,
                (message, acknowledge) -> {
                    throw new ExpectedTestException();
                }
            );

            // act
            final CompletableFuture<?> result = processor.processMessage(message, resolveMessage);

            // assert
            assertThat(result).isCompletedExceptionally();
            verify(resolveMessage, never()).get();
        }
    }

    @Nested
    class ConsumeMessageWithAcknowledgeAndVisibilityExtender {

        @Test
        void successfulExecutionWillResolveFutureButNotResolveMessage() {
            // arrange
            final LambdaMessageProcessor processor = new LambdaMessageProcessor(
                sqsAsyncClient,
                queueProperties,
                (message, acknowledge, visibilityExtender) -> {}
            );

            // act
            final CompletableFuture<?> result = processor.processMessage(message, resolveMessage);

            // assert
            assertThat(result).isCompleted();
            verify(resolveMessage, never()).get();
        }
    }

    @Nested
    class ResolvingMessage {

        @Test
        void exceptionThrownWhenResolvingMessageWillNotRejectProcessingFuture() {
            // arrange
            final LambdaMessageProcessor processor = new LambdaMessageProcessor(sqsAsyncClient, queueProperties, message -> {});
            when(resolveMessage.get()).thenThrow(new ExpectedTestException());

            // act
            final CompletableFuture<?> result = processor.processMessage(message, resolveMessage);

            // assert
            assertThat(result).isCompleted();
        }

        @Test
        void resolveMessageRejectedWillNotRejectProcessingFuture() {
            // arrange
            final LambdaMessageProcessor processor = new LambdaMessageProcessor(sqsAsyncClient, queueProperties, message -> {});
            when(resolveMessage.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));

            // act
            final CompletableFuture<?> result = processor.processMessage(message, resolveMessage);

            // assert
            assertThat(result).isCompleted();
        }
    }
}
