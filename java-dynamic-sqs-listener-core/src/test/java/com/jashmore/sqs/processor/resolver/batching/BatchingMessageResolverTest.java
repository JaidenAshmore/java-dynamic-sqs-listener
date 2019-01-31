package com.jashmore.sqs.processor.resolver.batching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import org.junit.Before;
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
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BatchingMessageResolverTest {
    private static final int BUFFERING_TIME_IN_MS = 500;
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl("queueUrl")
            .build();

    private static final BatchingMessageResolverProperties BATCHING_PROPERTIES = StaticBatchingMessageResolverProperties.builder()
            .bufferingTimeInMs(BUFFERING_TIME_IN_MS)
            .bufferingSizeLimit(2)
            .build();
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private ExecutorService mockExecutorService;

    @Mock
    private Future<Object> mockFuture;

    private BatchingMessageResolver batchingMessageResolver;

    @Before
    public void setUp() {
        batchingMessageResolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, BATCHING_PROPERTIES);
    }

    @Test
    public void noBackgroundThreadsShouldBeRunningIfNoMessagesAreBeingExecuted() {
        // act
        new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, BATCHING_PROPERTIES, mockExecutorService);

        // assert
        verify(mockExecutorService, never()).submit(any(Runnable.class));
    }

    @Test
    public void whenMessageRequestedToBeResolvedTheBackgroundThreadForBufferingShouldBeStarted() {
        // arrange
        final BatchingMessageResolver resolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, BATCHING_PROPERTIES, mockExecutorService);

        // act
        resolver.resolveMessage(Message.builder().body("test").build());

        // assert
        verify(mockExecutorService).submit(any(Runnable.class));
    }

    @Test
    public void messageShouldBeBufferedUntilTheTimeLimitIsHit() throws Exception {
        // arrange
        final CountDownLatch messageDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    messageDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build());
                });

        // act
        final long timeStarted = System.currentTimeMillis();
        batchingMessageResolver.resolveMessage(Message.builder().body("test").build());
        messageDeletedLatch.await(1, TimeUnit.SECONDS);

        // assert
        final long totalTime = System.currentTimeMillis() - timeStarted;
        assertThat(totalTime).isGreaterThanOrEqualTo(BUFFERING_TIME_IN_MS);
    }

    @Test
    public void whenBufferingSizeLimitHitTheMessagesAreBatchedBeforeTheTimeLimitEnds() throws Exception {
        // arrange
        final CountDownLatch messageDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    messageDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build());
                });

        // act
        final long timeStarted = System.currentTimeMillis();
        batchingMessageResolver.resolveMessage(Message.builder().body("test").build());
        batchingMessageResolver.resolveMessage(Message.builder().body("test").build());
        messageDeletedLatch.await(1, TimeUnit.SECONDS);

        // assert
        final long totalTime = System.currentTimeMillis() - timeStarted;
        assertThat(totalTime).isLessThanOrEqualTo(BUFFERING_TIME_IN_MS);
    }

    @Test
    public void batchDeleteRequestShouldContainAllTheCorrectInformation() throws Exception {
        // arrange
        final CountDownLatch messageDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    messageDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build());
                });

        // act
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id1").body("test").receiptHandle("receipt1").build());
        batchingMessageResolver.resolveMessage(Message.builder().messageId("id2").body("test").receiptHandle("receipt2").build());
        messageDeletedLatch.await(1, TimeUnit.SECONDS);

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
    public void whenTwoMessagesAreSubmittedBeforeBufferLimitOnlyOneBackgroundThreadIsUsed() {
        // arrange
        final BatchingMessageResolver resolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, BATCHING_PROPERTIES, mockExecutorService);
        doReturn(mockFuture).when(mockExecutorService).submit(any(Runnable.class));


        // act
        resolver.resolveMessage(Message.builder().body("test").build());
        resolver.resolveMessage(Message.builder().body("test").build());

        // assert
        verify(mockExecutorService, times(1)).submit(any(Runnable.class));
    }

    @Test
    public void whenBufferedBatchDeletedAnyFutureMessageWillBePlacedOnADifferentBackgroundThread() throws Exception {
        // arrange
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build()));

        // act
        batchingMessageResolver.resolveMessage(Message.builder().body("test").build());
        final Future<?> firstFuture = batchingMessageResolver.backgroundDeletionFuture;
        firstFuture.get(1, TimeUnit.SECONDS);
        batchingMessageResolver.resolveMessage(Message.builder().body("test").build());
        final Future<?> secondFuture = batchingMessageResolver.backgroundDeletionFuture;

        // assert
        assertThat(firstFuture).isNotEqualTo(secondFuture);
    }

    @Test
    public void errorDeletingMessageStillAllowsFutureMessagesToBeResolved() throws Exception {
        // arrange
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    final CompletableFuture<?> future = new CompletableFuture<>();
                    future.completeExceptionally(new RuntimeException("error"));
                    return future;
                })
                .thenAnswer(invocation -> CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build()));

        // act
        batchingMessageResolver.resolveMessage(Message.builder().body("test").build());
        final Future<?> firstFuture = batchingMessageResolver.backgroundDeletionFuture;
        firstFuture.get(1, TimeUnit.SECONDS);
        batchingMessageResolver.resolveMessage(Message.builder().body("test").build());
        final Future<?> secondFuture = batchingMessageResolver.backgroundDeletionFuture;
        secondFuture.get(1, TimeUnit.SECONDS);

        // assert
        verify(sqsAsyncClient, times(2)).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
    }

    @Test
    public void errorWithIndividualMessagesBeingDeletedStillAllowsFutureMessagesToBeResolved() throws Exception {
        // arrange
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder()
                        .failed(BatchResultErrorEntry.builder().id("id").build(), BatchResultErrorEntry.builder().id("id2").build())
                        .build()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build()));

        // act
        batchingMessageResolver.resolveMessage(Message.builder().body("test").build());
        final Future<?> firstFuture = batchingMessageResolver.backgroundDeletionFuture;
        firstFuture.get(1, TimeUnit.SECONDS);
        batchingMessageResolver.resolveMessage(Message.builder().body("test").build());
        final Future<?> secondFuture = batchingMessageResolver.backgroundDeletionFuture;
        secondFuture.get(1, TimeUnit.SECONDS);

        // assert
        verify(sqsAsyncClient, times(2)).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
    }

    @Test
    public void interruptingBackgroundThreadWhileWaitingWillSubmitCurrentMessagesBuffered() throws Exception {
        // arrange
        final CountDownLatch startedBufferingWait = new CountDownLatch(1);
        final BatchingMessageResolverProperties batchingMessageResolverProperties = mock(BatchingMessageResolverProperties.class);
        when(batchingMessageResolverProperties.getBufferingSizeLimit()).thenReturn(2);
        when(batchingMessageResolverProperties.getBufferingTimeInMs()).thenAnswer(invocation -> {
            startedBufferingWait.countDown();
            return BUFFERING_TIME_IN_MS;
        });
        final CountDownLatch messageDeletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(invocation -> {
                    messageDeletedLatch.countDown();
                    return CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build());
                });
        final BatchingMessageResolver resolver = new BatchingMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient, batchingMessageResolverProperties);

        // act
        resolver.resolveMessage(Message.builder().body("test").build());
        startedBufferingWait.await(1, TimeUnit.SECONDS);
        final Future<?> firstFuture = resolver.backgroundDeletionFuture;
        firstFuture.cancel(true);
        messageDeletedLatch.await(1, TimeUnit.SECONDS);

        // assert
        verify(sqsAsyncClient).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
    }
}