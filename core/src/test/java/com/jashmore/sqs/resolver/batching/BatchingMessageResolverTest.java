package com.jashmore.sqs.resolver.batching;

import static com.jashmore.sqs.util.thread.ThreadTestUtils.waitUntilThreadInState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.util.ExpectedTestException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
@ExtendWith(MockitoExtension.class)
class BatchingMessageResolverTest {

    private static final Duration BUFFERING_TIME = Duration.ofSeconds(1);
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder().queueUrl("queueUrl").build();

    private static final StaticBatchingMessageResolverProperties DEFAULT_BATCHING_PROPERTIES = StaticBatchingMessageResolverProperties
        .builder()
        .bufferingTime(BUFFERING_TIME)
        .bufferingSizeLimit(1)
        .build();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void messageShouldBeBufferedUntilTheTimeLimitIsHit() throws Exception {
        // arrange
        final long bufferingTimeInMs = 100;
        final BatchingMessageResolverProperties batchingProperties = DEFAULT_BATCHING_PROPERTIES
            .toBuilder()
            .bufferingTime(Duration.ofMillis(bufferingTimeInMs))
            .bufferingSizeLimit(2)
            .build();
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(1);
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(
            QUEUE_PROPERTIES,
            sqsAsyncClient,
            batchingProperties
        );
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id").receiptHandle("handle").build());
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    batchBeingDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(
                        DeleteMessageBatchResponse.builder().successful(DeleteMessageBatchResultEntry.builder().id("id").build()).build()
                    );
                }
            );

        // act
        final long startTime = System.currentTimeMillis();
        executorService.submit(batchingMessageResolver::run);
        assertThat(batchBeingDeletedLatch.await(1, TimeUnit.SECONDS)).isTrue();
        final long endTime = System.currentTimeMillis();

        // assert
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(bufferingTimeInMs);
    }

    @Test
    void whenBatchingSizeLimitReachedTheMessagesAreImmediatelySent() throws Exception {
        // arrange
        final long bufferingTimeInMs = 100_000;
        final BatchingMessageResolverProperties batchingProperties = DEFAULT_BATCHING_PROPERTIES
            .toBuilder()
            .bufferingTime(Duration.ofMillis(bufferingTimeInMs))
            .bufferingSizeLimit(2)
            .build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(
            QUEUE_PROPERTIES,
            sqsAsyncClient,
            batchingProperties
        );
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id").receiptHandle("handle").build());
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id2").receiptHandle("handle2").build());
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    batchBeingDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build());
                }
            );

        // act
        final long startTime = System.currentTimeMillis();
        executorService.submit(batchingMessageResolver::run);
        assertThat(batchBeingDeletedLatch.await(1, TimeUnit.SECONDS)).isTrue();
        final long endTime = System.currentTimeMillis();

        // assert
        assertThat(endTime - startTime).isLessThan(bufferingTimeInMs);
    }

    @Test
    void batchDeleteRequestShouldContainCorrectReceiptHandleForMessageRemoval() throws Exception {
        // arrange
        final long bufferingTimeInMs = 100_000;
        final BatchingMessageResolverProperties batchingProperties = DEFAULT_BATCHING_PROPERTIES
            .toBuilder()
            .bufferingTime(Duration.ofMillis(bufferingTimeInMs))
            .bufferingSizeLimit(2)
            .build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(
            QUEUE_PROPERTIES,
            sqsAsyncClient,
            batchingProperties
        );
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    batchBeingDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build());
                }
            );
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id1").receiptHandle("receipt1").build());
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id2").receiptHandle("receipt2").build());

        // act
        executorService.submit(batchingMessageResolver::run);
        assertThat(batchBeingDeletedLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // assert
        verify(sqsAsyncClient)
            .deleteMessageBatch(
                DeleteMessageBatchRequest
                    .builder()
                    .queueUrl("queueUrl")
                    .entries(
                        DeleteMessageBatchRequestEntry.builder().id("id1").receiptHandle("receipt1").build(),
                        DeleteMessageBatchRequestEntry.builder().id("id2").receiptHandle("receipt2").build()
                    )
                    .build()
            );
    }

    @Test
    void whenMessageIsSuccessfullyDeletedTheCompletableFutureIsResolved() throws Exception {
        // arrange
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(
            QUEUE_PROPERTIES,
            sqsAsyncClient,
            DEFAULT_BATCHING_PROPERTIES
        );
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    batchBeingDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(
                        DeleteMessageBatchResponse.builder().successful(DeleteMessageBatchResultEntry.builder().id("id").build()).build()
                    );
                }
            );
        final CompletableFuture<?> messageResolvedCompletableFuture = batchingMessageResolver.resolveMessage(
            Message.builder().messageId("id").receiptHandle("handle").build()
        );

        // act
        executorService.submit(batchingMessageResolver::run);
        assertThat(batchBeingDeletedLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // assert
        messageResolvedCompletableFuture.get();
        assertThat(messageResolvedCompletableFuture).isCompleted();
    }

    @Test
    void whenMessageFailsToBeDeletedTheCompletableFutureIsCompletedExceptionally() throws Exception {
        // arrange
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(
            QUEUE_PROPERTIES,
            sqsAsyncClient,
            DEFAULT_BATCHING_PROPERTIES
        );
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    batchBeingDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(
                        DeleteMessageBatchResponse
                            .builder()
                            .failed(BatchResultErrorEntry.builder().id("id").message("Expected Test Error").build())
                            .build()
                    );
                }
            );
        final CompletableFuture<?> messageResolvedCompletableFuture = batchingMessageResolver.resolveMessage(
            Message.builder().messageId("id").receiptHandle("handle").build()
        );

        // act
        executorService.submit(batchingMessageResolver::run);
        assertThat(batchBeingDeletedLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // assert
        final ExecutionException exception = assertThrows(ExecutionException.class, messageResolvedCompletableFuture::get);
        assertThat(exception).hasCauseInstanceOf(RuntimeException.class);
        assertThat(exception.getCause()).hasMessage("Expected Test Error");
    }

    @Test
    void whenMessageIsNotHandledInBatchDeletionItIsRejected() throws Exception {
        // arrange
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(
            QUEUE_PROPERTIES,
            sqsAsyncClient,
            DEFAULT_BATCHING_PROPERTIES
        );
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    batchBeingDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build());
                }
            );
        final CompletableFuture<?> messageResolvedCompletableFuture = batchingMessageResolver.resolveMessage(
            Message.builder().messageId("id").receiptHandle("handle").build()
        );

        // act
        executorService.submit(batchingMessageResolver::run);
        assertThat(batchBeingDeletedLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // assert
        final ExecutionException exception = assertThrows(ExecutionException.class, messageResolvedCompletableFuture::get);
        assertThat(exception).hasCauseInstanceOf(RuntimeException.class);
        assertThat(exception.getCause()).hasMessage("Message not handled by batch delete. This should not happen");
    }

    @Test
    void notProvidingPropertiesWillResolveMessagesAsSoonAsTheyAreRequested() throws Exception {
        // arrange
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient);
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    batchBeingDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build());
                }
            );
        executorService.submit(batchingMessageResolver::run);

        // act
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id").receiptHandle("handle").build());

        // assert
        assertThat(batchBeingDeletedLatch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void interruptingThreadWhileWaitingForTotalMessageBatchWillStillPublishCurrentMessagesObtained() throws Exception {
        // arrange
        final StaticBatchingMessageResolverProperties batchingProperties = DEFAULT_BATCHING_PROPERTIES
            .toBuilder()
            .bufferingTime(Duration.ofMillis(100_000L))
            .bufferingSizeLimit(2)
            .build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(
            QUEUE_PROPERTIES,
            sqsAsyncClient,
            batchingProperties
        );
        final CompletableFuture<?> messageResolvedFuture = batchingMessageResolver.resolveMessage(
            Message.builder().messageId("id").receiptHandle("handle").build()
        );
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation ->
                    CompletableFuture.completedFuture(
                        DeleteMessageBatchResponse.builder().successful(DeleteMessageBatchResultEntry.builder().id("id").build()).build()
                    )
            );

        // act
        final Future<?> resolverThreadFuture = executorService.submit(batchingMessageResolver::run);
        Thread.sleep(500); // Just make sure we have drained the single message
        resolverThreadFuture.cancel(true);

        // assert
        messageResolvedFuture.get(30, TimeUnit.SECONDS);
        assertThat(messageResolvedFuture).isCompleted();
    }

    @Test
    void exceptionThrownInResponseToBatchRemovalWillRejectAllMessages() throws Exception {
        // arrange
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(
            QUEUE_PROPERTIES,
            sqsAsyncClient,
            DEFAULT_BATCHING_PROPERTIES
        );
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    batchBeingDeletedLatch.countDown();
                    final CompletableFuture<DeleteMessageBatchResponse> completableFuture = new CompletableFuture<>();
                    completableFuture.completeExceptionally(new ExpectedTestException());
                    return completableFuture;
                }
            );
        final CompletableFuture<?> messageResolvedFuture = batchingMessageResolver.resolveMessage(
            Message.builder().messageId("id").receiptHandle("handle").build()
        );

        // act
        executorService.submit(batchingMessageResolver::run);
        assertThat(batchBeingDeletedLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // assert
        final ExecutionException exception = assertThrows(ExecutionException.class, messageResolvedFuture::get);
        assertThat(exception).hasCauseInstanceOf(ExpectedTestException.class);
    }

    @Test
    void exceptionThrownSendingBatchRemovalWillRejectAllMessages() {
        // arrange
        final StaticBatchingMessageResolverProperties properties = DEFAULT_BATCHING_PROPERTIES.toBuilder().bufferingSizeLimit(1).build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, properties);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class))).thenThrow(new ExpectedTestException());
        final CompletableFuture<?> messageResolvedFuture = batchingMessageResolver.resolveMessage(
            Message.builder().messageId("id").receiptHandle("handle").build()
        );

        // act
        executorService.submit(batchingMessageResolver::run);

        // assert
        final ExecutionException exception = assertThrows(ExecutionException.class, messageResolvedFuture::get);
        assertThat(exception).hasCauseInstanceOf(ExpectedTestException.class);
    }

    @Test
    void multipleBatchesOfDeletionsCanBeSentConcurrentlyIfManyResolveMessageCallsAreMadeAtOnce() throws Exception {
        // arrange
        final StaticBatchingMessageResolverProperties properties = DEFAULT_BATCHING_PROPERTIES.toBuilder().bufferingSizeLimit(1).build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, properties);
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(2);
        final CountDownLatch blockDeleteMessage = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    log.debug("Received batch to delete");
                    batchBeingDeletedLatch.countDown();
                    blockDeleteMessage.await();
                    throw new ExpectedTestException();
                }
            );
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id").receiptHandle("handle").build());
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id").receiptHandle("handle").build());

        // act
        executorService.submit(batchingMessageResolver::run);

        // assert
        assertThat(batchBeingDeletedLatch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shuttingDownMessageResolverWillWaitUntilEachMessageBatchCompletes() throws Exception {
        // arrange
        final StaticBatchingMessageResolverProperties properties = DEFAULT_BATCHING_PROPERTIES.toBuilder().bufferingSizeLimit(1).build();
        final BatchingMessageResolver batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, properties);
        final CountDownLatch batchBeingDeletedLatch = new CountDownLatch(1);
        final CountDownLatch blockDeleteMessage = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
            .thenAnswer(
                invocation -> {
                    batchBeingDeletedLatch.countDown();
                    blockDeleteMessage.await();
                    throw new ExpectedTestException();
                }
            );
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id").receiptHandle("handle").build());
        final Thread resolverThread = new Thread(batchingMessageResolver::run);
        resolverThread.start();
        assertThat(batchBeingDeletedLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // act
        resolverThread.interrupt();

        // assert
        Thread.sleep(500);
        waitUntilThreadInState(resolverThread, Thread.State.WAITING);
        blockDeleteMessage.countDown();
        waitUntilThreadInState(resolverThread, Thread.State.TERMINATED);
    }
}
