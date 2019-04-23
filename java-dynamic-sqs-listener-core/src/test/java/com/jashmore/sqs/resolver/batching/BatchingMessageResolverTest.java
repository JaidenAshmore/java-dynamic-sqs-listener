package com.jashmore.sqs.resolver.batching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BatchingMessageResolverTest {
    private static final int BUFFERING_TIME_IN_MS = 10;
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl("queueUrl")
            .build();

    private static final StaticBatchingMessageResolverProperties DEFAULT_BATCHING_PROPERTIES = StaticBatchingMessageResolverProperties.builder()
            .bufferingTimeInMs(BUFFERING_TIME_IN_MS)
            .bufferingSizeLimit(1)
            .build();
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Test
    public void messageShouldBeBufferedUntilTheTimeLimitIsHit() throws Exception {
        // arrange
        final long bufferingTimeInMs = 100;
        final BatchingMessageResolverProperties batchingProperties = DEFAULT_BATCHING_PROPERTIES.toBuilder()
                .bufferingTimeInMs(bufferingTimeInMs)
                .bufferingSizeLimit(2)
                .build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, batchingProperties);
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id").receiptHandle("handle").build());
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt(); // stop the background thread
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder()
                            .successful(DeleteMessageBatchResultEntry.builder()
                                    .id("id")
                                    .build()
                            )
                            .build());
                });

        // act
        final long startTime = System.currentTimeMillis();
        CompletableFuture.runAsync(batchingMessageResolver).get();
        final long endTime = System.currentTimeMillis();

        // assert
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(bufferingTimeInMs);
    }

    @Test
    public void whenBatchingSizeLimitReachedTheMessagesAreImmediatelySent() throws Exception {
        // arrange
        final long bufferingTimeInMs = 100_000;
        final BatchingMessageResolverProperties batchingProperties = DEFAULT_BATCHING_PROPERTIES.toBuilder()
                .bufferingTimeInMs(bufferingTimeInMs)
                .bufferingSizeLimit(2)
                .build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, batchingProperties);
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id").receiptHandle("handle").build());
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id2").receiptHandle("handle2").build());
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt(); // stop the background thread
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder()
                            .build());
                });

        // act
        final long startTime = System.currentTimeMillis();
        CompletableFuture.runAsync(batchingMessageResolver).get();
        final long endTime = System.currentTimeMillis();

        // assert
        assertThat(endTime - startTime).isLessThan(bufferingTimeInMs);
    }

    @Test
    public void batchDeleteRequestShouldContainCorrectReceiptHandleForMessageRemoval() throws Exception {
        // arrange
        final long bufferingTimeInMs = 100_000;
        final BatchingMessageResolverProperties batchingProperties = DEFAULT_BATCHING_PROPERTIES.toBuilder()
                .bufferingTimeInMs(bufferingTimeInMs)
                .bufferingSizeLimit(2)
                .build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, batchingProperties);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt(); // stop the background thread
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder()
                            .build());
                });
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id1").receiptHandle("receipt1").build());
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id2").receiptHandle("receipt2").build());

        // act
        CompletableFuture.runAsync(batchingMessageResolver).get();

        // assert
        verify(sqsAsyncClient).deleteMessageBatch(DeleteMessageBatchRequest.builder()
                .queueUrl("queueUrl")
                .entries(
                        DeleteMessageBatchRequestEntry.builder().id("id1").receiptHandle("receipt1").build(),
                        DeleteMessageBatchRequestEntry.builder().id("id2").receiptHandle("receipt2").build()
                )
                .build());
    }

    @Test
    public void whenMessageIsSuccessfullyDeletedTheCompletableFutureIsResolved() throws Exception {
        // arrange
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, DEFAULT_BATCHING_PROPERTIES);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt(); // stop the background thread
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder()
                            .successful(DeleteMessageBatchResultEntry.builder()
                                    .id("id")
                                    .build()
                            )
                            .build());
                });
        final CompletableFuture<?> messageResolvedCompletableFuture = batchingMessageResolver.resolveMessage(Message.builder()
                .messageId("id")
                .receiptHandle("handle")
                .build());

        // act
        CompletableFuture.runAsync(batchingMessageResolver).get();

        // assert
        messageResolvedCompletableFuture.get();
        assertThat(messageResolvedCompletableFuture).isCompleted();
    }

    @Test
    public void whenMessageFailsToBeDeletedTheCompletableFutureIsCompletedExceptionally() throws Exception {
        // arrange
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, DEFAULT_BATCHING_PROPERTIES);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt(); // stop the background thread
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder()
                            .failed(BatchResultErrorEntry.builder()
                                    .id("id")
                                    .message("Expected Error")
                                    .build())
                            .build());
                });
        final CompletableFuture<?> messageResolvedCompletableFuture = batchingMessageResolver.resolveMessage(Message.builder()
                .messageId("id")
                .receiptHandle("handle")
                .build());

        // act
        CompletableFuture.runAsync(batchingMessageResolver).get();

        // assert
        try {
            messageResolvedCompletableFuture.get();
            fail("Should have failed to resolve message");
        } catch (final ExecutionException executionException) {
            assertThat(executionException.getCause()).isInstanceOf(RuntimeException.class);
            assertThat(executionException.getCause()).hasMessage("Expected Error");
        }
    }

    @Test
    public void whenMessageIsMissingFromTheResultsTheCompletableFutureIsCompletedExceptionally() throws Exception {
        // arrange
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, DEFAULT_BATCHING_PROPERTIES);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt(); // stop the background thread
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder()
                            .build());
                });
        final CompletableFuture<?> messageResolvedCompletableFuture = batchingMessageResolver.resolveMessage(Message.builder()
                .messageId("id")
                .receiptHandle("handle")
                .build());

        // act
        CompletableFuture.runAsync(batchingMessageResolver).get();

        // assert
        try {
            messageResolvedCompletableFuture.get();
            fail("Should have failed to resolve message");
        } catch (final ExecutionException executionException) {
            assertThat(executionException.getCause()).isInstanceOf(RuntimeException.class);
            assertThat(executionException.getCause()).hasMessage("Message not handled by batch delete. This should not happen");
        }
    }

    @Test
    public void interruptingThreadWhileWaitingForTotalMessageBatchWillStillPublishCurrentMessagesObtained() throws Exception {
        // arrange
        final StaticBatchingMessageResolverProperties batchingProperties = DEFAULT_BATCHING_PROPERTIES.toBuilder()
                .bufferingTimeInMs(100_000L)
                .bufferingSizeLimit(2)
                .build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, batchingProperties);
        final CompletableFuture<?> messageResolvedFuture = batchingMessageResolver.resolveMessage(Message.builder()
                .messageId("id")
                .receiptHandle("handle")
                .build());
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder()
                        .successful(DeleteMessageBatchResultEntry.builder()
                                .id("id")
                                .build())
                        .build()));

        // act
        final Future<?> resolverThreadFuture = Executors.newCachedThreadPool().submit(batchingMessageResolver);
        Thread.sleep(500); // Just make sure we have drained the single message
        resolverThreadFuture.cancel(true);

        // assert
        messageResolvedFuture.get();
        assertThat(messageResolvedFuture).isCompleted();
    }

    @Test
    public void exceptionThrownSubmittingMessageResolutionBatchWillRejectAllMessages() throws Exception {
        // arrange
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, DEFAULT_BATCHING_PROPERTIES);
        final CompletableFuture<?> messageResolvedFuture = batchingMessageResolver.resolveMessage(Message.builder()
                .messageId("id")
                .receiptHandle("handle")
                .build());
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt();
                    final CompletableFuture<DeleteMessageBatchResponse> completableFuture = new CompletableFuture<>();
                    completableFuture.completeExceptionally(new RuntimeException("Expected Test Exception"));
                    return completableFuture;
                });

        // act
        Executors.newCachedThreadPool().submit(batchingMessageResolver).get();

        // assert
        assertThat(messageResolvedFuture).isCompletedExceptionally();
    }

}