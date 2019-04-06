package com.jashmore.sqs.retriever.batching;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class BatchingMessageRetrieverTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl(QUEUE_URL)
            .build();

    private static final int POLLING_PERIOD_IN_MS = 100;
    private static final StaticBatchingMessageRetrieverProperties DEFAULT_PROPERTIES = StaticBatchingMessageRetrieverProperties
            .builder()
            .visibilityTimeoutInSeconds(10)
            .numberOfThreadsWaitingTrigger(2)
            .messageRetrievalPollingPeriodInMs(POLLING_PERIOD_IN_MS)
            .waitTimeInSeconds(10)
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private ExecutorService mockExecutorService;

    @Mock
    private Future<?> mockFuture;

    @Captor
    private ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor;

    @Test
    public void whenStartingRetrieverTheBackgroundThreadShouldBeStarted() {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                mockExecutorService, DEFAULT_PROPERTIES);

        // act
        batchingMessageRetriever.start();

        // assert
        verify(mockExecutorService).submit(any(BatchingMessageRetriever.BackgroundBatchingMessageRetriever.class));
    }

    @Test
    public void whenStartingRetrieverTwiceNoSecondBackgroundThreadIsStarted() {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                mockExecutorService, DEFAULT_PROPERTIES);

        // act
        batchingMessageRetriever.start();
        batchingMessageRetriever.start();

        // assert
        verify(mockExecutorService, times(1)).submit(any(BatchingMessageRetriever.BackgroundBatchingMessageRetriever.class));
    }

    @Test
    public void stoppingRetrieverThatHasNotStartedDoesNothing() throws ExecutionException, InterruptedException {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                mockExecutorService, DEFAULT_PROPERTIES);

        // act
        final Future<?> stoppingFuture = batchingMessageRetriever.stop();

        // assert
        assertThat(stoppingFuture.get()).isEqualTo("Not running");
    }

    @Test
    public void stoppingStartedRetrieverCancelsBackgroundThread() {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                mockExecutorService, DEFAULT_PROPERTIES);
        doReturn(mockFuture).when(mockExecutorService).submit(any(BatchingMessageRetriever.BackgroundBatchingMessageRetriever.class));
        batchingMessageRetriever.start();

        // act
        batchingMessageRetriever.stop();

        // assert
        verify(mockFuture).cancel(true);
    }

    /**
     * The reason for this test is that the way to stop the background thread is by cancelling it (interrupting it) and therefore if we return that future
     * it will throw a {@link CancellationException}. To mitigate this we require that a different future is returned that would resolve when the background
     * thread has been successfully stopped.
     */
    @Test
    public void futureReturnedWhenStoppingMessageRetrieverIsNotBackgroundThreadFuture() {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                mockExecutorService, DEFAULT_PROPERTIES);
        doReturn(mockFuture).when(mockExecutorService).submit(any(BatchingMessageRetriever.BackgroundBatchingMessageRetriever.class));
        batchingMessageRetriever.start();

        // act
        final Future<?> stoppingFuture = batchingMessageRetriever.stop();

        // assert
        assertThat(stoppingFuture).isNotEqualTo(mockFuture);
    }

    @Test
    public void backgroundThreadWillNotAttemptToGetMessagesIfNoThreadsRequestingIt() throws InterruptedException {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                Executors.newCachedThreadPool(), DEFAULT_PROPERTIES);
        batchingMessageRetriever.start();

        // act
        Thread.sleep(2 * POLLING_PERIOD_IN_MS);

        // assert
        verify(sqsAsyncClient, never()).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void whenThePollingPeriodIsHitTheBackgroundThreadWillRequestAsManyMessagesAsThoseWaiting() throws Exception {
        // arrange
        final int pollingPeriodInMs = 500;
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder().messageRetrievalPollingPeriodInMs(pollingPeriodInMs).build();
        final BatchingMessageRetriever batchingMessageRetriever = batchingMessageRetriever(properties);
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().build());
        });
        final long startTime = System.currentTimeMillis();
        batchingMessageRetriever.start();

        // act
        final Future<?> messageRetrievingFuture = requestMessageOnNewThread(batchingMessageRetriever);
        waitForLatch(receivedMessageLatch, 1000);
        final long timeTaken = System.currentTimeMillis() - startTime;
        batchingMessageRetriever.stop().get(1, SECONDS);
        messageRetrievingFuture.cancel(true);

        // assert
        verify(sqsAsyncClient, times(1)).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(timeTaken).isGreaterThanOrEqualTo(pollingPeriodInMs);
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(1);
    }

    @Test
    public void backgroundThreadWillRequestMessagesWhenLimitReached() throws Exception {
        // arrange
        final int pollingPeriodInMs = 10000;
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES
                .toBuilder()
                .numberOfThreadsWaitingTrigger(1)
                .messageRetrievalPollingPeriodInMs(pollingPeriodInMs)
                .build();
        final BatchingMessageRetriever batchingMessageRetriever = batchingMessageRetriever(properties);
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(Message.builder().build()).build());
        });
        batchingMessageRetriever.start();

        // act
        final Future<?> messageRetrievalFuture = requestMessageOnNewThread(batchingMessageRetriever);

        // assert
        waitForLatch(receivedMessageLatch, pollingPeriodInMs / 4);

        // cleanup
        batchingMessageRetriever.stop().get(1, SECONDS);
        messageRetrievalFuture.cancel(true);
    }

    @Test
    public void errorObtainingMessagesWillTryAgainAfterPollingPeriodExpiresAgainWhenWaitingLimitIsNotReached() throws Exception {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = batchingMessageRetriever();
        final Message message = Message.builder().build();
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new RuntimeException("error"))
                .thenAnswer(invocation -> {
                    receivedMessageLatch.countDown();
                    return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message).build());
                });
        final long startTime = System.currentTimeMillis();
        batchingMessageRetriever.start();

        // act
        final Message messageRetrieved = batchingMessageRetriever.retrieveMessage();
        final long actualTime = System.currentTimeMillis() - startTime;
        batchingMessageRetriever.stop().get(1, SECONDS);

        // assert
        verify(sqsAsyncClient, times(2)).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(messageRetrieved).isEqualTo(message);
        assertThat(actualTime).isGreaterThanOrEqualTo(POLLING_PERIOD_IN_MS * 2);
    }

    @Test
    public void willNotExceedAwsMaxMessagesForRetrievalWhenRequestingMessages() throws Exception {
        // arrange
        final StaticBatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder().numberOfThreadsWaitingTrigger(10).build();
        final BatchingMessageRetriever batchingMessageRetriever = batchingMessageRetriever(properties);
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
                    if (receivedMessageLatch.getCount() == 0) {
                        try {
                            testCompletedLatch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return ReceiveMessageResponse.builder().build();
                        }
                    }
                    receivedMessageLatch.countDown();
                    try {
                        testCompletedLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return ReceiveMessageResponse.builder().build();
                }));
        batchingMessageRetriever.start();

        // act
        final Set<? extends Future<?>> allMessagesFutures = IntStream.range(0, 12)
                .mapToObj(i -> requestMessageOnNewThread(batchingMessageRetriever))
                .collect(Collectors.toSet());
        waitForLatch(receivedMessageLatch, 1000);

        // cleanup
        allMessagesFutures.forEach(future -> future.cancel(true));
        final Future<Object> stoppingFuture = batchingMessageRetriever.stop();
        testCompletedLatch.countDown();
        stoppingFuture.get(1, SECONDS);

        // assert
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
    }

    @Test
    public void consumerThatIsInterruptedWhileMessageIsBeingDownloadedWillPlaceMessageOnAQueueForInstantRetrievalNextTime() throws Exception {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = batchingMessageRetriever();
        final Message message = Message.builder().build();
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        final CountDownLatch consumerTimedOutLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            consumerTimedOutLatch.await();
            return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message).build());
        });
        batchingMessageRetriever.start();
        final Future<?> retrieveMessageFuture = obtainFutureOnBackgroundThreadWhenInterrupted(batchingMessageRetriever, consumerTimedOutLatch);

        // act
        waitForLatch(receivedMessageLatch, POLLING_PERIOD_IN_MS * 2);
        retrieveMessageFuture.cancel(true);
        waitForLatch(consumerTimedOutLatch, 1000);
        final Message messageRetrieved = batchingMessageRetriever.retrieveMessage();
        batchingMessageRetriever.stop().get(1, SECONDS);

        // assert
        verify(sqsAsyncClient, times(1)).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(messageRetrieved).isEqualTo(message);
    }

    @Test
    public void waitTimeProvidedInPropertiesIsUsedForMessageRetrievalRequests() throws Exception {
        final int pollingPeriodInMs = 10000;
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES
                .toBuilder()
                .numberOfThreadsWaitingTrigger(1)
                .messageRetrievalPollingPeriodInMs(pollingPeriodInMs)
                .build();
        final BatchingMessageRetriever batchingMessageRetriever = batchingMessageRetriever(properties);
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(Message.builder().build()).build());
        });
        batchingMessageRetriever.start();

        // act
        requestMessageOnNewThread(batchingMessageRetriever);
        waitForLatch(receivedMessageLatch, pollingPeriodInMs / 4);

        // assert
        verify(sqsAsyncClient).receiveMessage(ReceiveMessageRequest
                .builder()
                .visibilityTimeout(properties.getVisibilityTimeoutInSeconds())
                .waitTimeSeconds(10)
                .maxNumberOfMessages(1)
                .queueUrl(QUEUE_URL)
                .build());
    }

    @Test
    public void nullPollingPeriodWillWaitUntilEnoughThreadsAreRequestingMessages() throws Exception {
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES
                .toBuilder()
                .numberOfThreadsWaitingTrigger(1)
                .messageRetrievalPollingPeriodInMs(null)
                .build();
        final BatchingMessageRetriever batchingMessageRetriever = batchingMessageRetriever(properties);
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(Message.builder().build()).build());
        });
        batchingMessageRetriever.start();

        // act
        requestMessageOnNewThread(batchingMessageRetriever);
        waitForLatch(receivedMessageLatch, 1000);

        // assert
        verify(sqsAsyncClient).receiveMessage(ReceiveMessageRequest
                .builder()
                .visibilityTimeout(properties.getVisibilityTimeoutInSeconds())
                .waitTimeSeconds(DEFAULT_PROPERTIES.getMessageWaitTimeInSeconds())
                .maxNumberOfMessages(1)
                .queueUrl(QUEUE_URL)
                .build());
    }

    @Test
    public void maximumWaitTimeIsUsedForMessageRetrievalRequestsWhenNoneProvided() throws Exception {
        final int pollingPeriodInMs = 10000;
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES
                .toBuilder()
                .numberOfThreadsWaitingTrigger(1)
                .messageRetrievalPollingPeriodInMs(pollingPeriodInMs)
                .waitTimeInSeconds(null)
                .build();
        final BatchingMessageRetriever batchingMessageRetriever = batchingMessageRetriever(properties);
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(Message.builder().build()).build());
        });
        batchingMessageRetriever.start();

        // act
        requestMessageOnNewThread(batchingMessageRetriever);
        waitForLatch(receivedMessageLatch, pollingPeriodInMs / 4);

        // assert
        verify(sqsAsyncClient).receiveMessage(ReceiveMessageRequest
                .builder()
                .visibilityTimeout(properties.getVisibilityTimeoutInSeconds())
                .waitTimeSeconds(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .maxNumberOfMessages(1)
                .queueUrl(QUEUE_URL)
                .build());
    }

    private Future<?> requestMessageOnNewThread(final BatchingMessageRetriever batchingMessageRetriever) {
        return obtainFutureOnBackgroundThreadWhenInterrupted(batchingMessageRetriever, null);
    }

    private Future<?> obtainFutureOnBackgroundThreadWhenInterrupted(final BatchingMessageRetriever batchingMessageRetriever,
                                                                    final CountDownLatch interruptedCountdownLatch) {
        return Executors.newCachedThreadPool().submit(() -> {
            try {
                batchingMessageRetriever.retrieveMessage();
            } catch (InterruptedException interruptedException) {
                if (interruptedCountdownLatch != null) {
                    interruptedCountdownLatch.countDown();
                }
            }
        });
    }

    private void waitForLatch(final CountDownLatch countDownLatch, final long timeInMs) throws InterruptedException {

        final boolean didGetLatch = countDownLatch.await(timeInMs, TimeUnit.MILLISECONDS);// This limit is considerable lower than the polling period above
        assertThat(didGetLatch).isTrue();
    }

    private BatchingMessageRetriever batchingMessageRetriever() {
        return batchingMessageRetriever(DEFAULT_PROPERTIES);
    }

    private BatchingMessageRetriever batchingMessageRetriever(final BatchingMessageRetrieverProperties properties) {
        return new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, Executors.newCachedThreadPool(), properties);
    }
}