package com.jashmore.sqs.retriever.batching;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
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

import java.util.Set;
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
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AmazonSQSAsync amazonSqsAsync;

    @Mock
    private ExecutorService mockExecutorService;

    @Mock
    private Future<?> mockFuture;

    @Captor
    private ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor;

    @Test
    public void whenStartingRetrieverBackgroundThreadShouldBeStarted() {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                mockExecutorService, DEFAULT_PROPERTIES);

        // act
        batchingMessageRetriever.start();

        // assert
        verify(mockExecutorService).submit(any(BatchingMessageRetriever.BackgroundBatchingMessageRetriever.class));
    }

    @Test
    public void startingRetrieverTwiceDoesNothing() {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
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
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                mockExecutorService, DEFAULT_PROPERTIES);

        // act
        final Future<?> stoppingFuture = batchingMessageRetriever.stop();

        // assert
        assertThat(stoppingFuture.get()).isEqualTo("Not running");
    }

    @Test
    public void stoppingStartedRetrieverCancelsBackgroundThread() {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                mockExecutorService, DEFAULT_PROPERTIES);
        doReturn(mockFuture).when(mockExecutorService).submit(any(BatchingMessageRetriever.BackgroundBatchingMessageRetriever.class));
        batchingMessageRetriever.start();

        // act
        batchingMessageRetriever.stop();

        // assert
        verify(mockFuture).cancel(true);
    }

    /**
     * The reason for this test is that we don't want the returned future from the stop function to be marked as cancelled.
     */
    @Test
    public void futureReturnedWhenStoppingMessageRetrieverIsNotBackgroundThreadFuture() {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
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
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                Executors.newCachedThreadPool(), DEFAULT_PROPERTIES);
        batchingMessageRetriever.start();

        // act
        Thread.sleep(2 * POLLING_PERIOD_IN_MS);

        // assert
        verify(amazonSqsAsync, never()).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void backgroundThreadWillOnlyRequestMessagesAfterTimeoutIfLimitNotReached() throws Exception {
        // arrange
        final int pollingPeriodInMs = 500;
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder().messageRetrievalPollingPeriodInMs(pollingPeriodInMs).build();
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                Executors.newCachedThreadPool(), properties);
        batchingMessageRetriever.start();
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(amazonSqsAsync.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            return new ReceiveMessageResult()
                    .withMessages(new Message());
        });

        // act
        final Future<?> messageRetrievingFuture = requestMessageOnNewThread(batchingMessageRetriever);
        Thread.sleep(50);
        verify(amazonSqsAsync, never()).receiveMessage(any(ReceiveMessageRequest.class));
        receivedMessageLatch.await(1, SECONDS);
        batchingMessageRetriever.stop().get(1, SECONDS);
        messageRetrievingFuture.cancel(true);

        // assert
        verify(amazonSqsAsync).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().getMaxNumberOfMessages()).isEqualTo(1);
    }

    @Test
    public void backgroundThreadWillRequestMessagesWhenLimitReached() throws Exception {
        // arrange
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES
                .toBuilder()
                .numberOfThreadsWaitingTrigger(1)
                .messageRetrievalPollingPeriodInMs(5000)
                .build();
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                Executors.newCachedThreadPool(), properties);
        batchingMessageRetriever.start();
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(amazonSqsAsync.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            return new ReceiveMessageResult()
                    .withMessages(new Message());
        });

        // act
        final Future<?> messageRetrievalFuture = requestMessageOnNewThread(batchingMessageRetriever);
        receivedMessageLatch.await(1000, TimeUnit.MILLISECONDS); // This limit is considerable lower than the polling period above
        batchingMessageRetriever.stop().get(1, SECONDS);
        messageRetrievalFuture.cancel(true);

        // assert
        verify(amazonSqsAsync).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().getMaxNumberOfMessages()).isEqualTo(1);
    }

    @Test
    public void consumerThatIsInterruptedWhileMessageIsBeingDownloadedWillPlaceMessageOnAQueueForInstantRetrievalNextTime() throws Exception {
        // arrange
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder().build();
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                Executors.newCachedThreadPool(), properties);
        final Message message = new Message();
        batchingMessageRetriever.start();
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        final CountDownLatch consumerTimedOutLatch = new CountDownLatch(1);
        when(amazonSqsAsync.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            consumerTimedOutLatch.await();
            log.info("Can return messages");
            return new ReceiveMessageResult()
                    .withMessages(message);
        });
        final Future<?> retrieveMessageFuture = obtainFutureOnBackgroundThreadWhenInterrupted(batchingMessageRetriever, consumerTimedOutLatch);

        // act
        receivedMessageLatch.await(POLLING_PERIOD_IN_MS * 2, TimeUnit.MILLISECONDS);
        retrieveMessageFuture.cancel(true);
        consumerTimedOutLatch.await(1000, TimeUnit.MILLISECONDS);
        final Message messageRetrieved = batchingMessageRetriever.retrieveMessage();
        log.info("Obtained message");
        batchingMessageRetriever.stop().get(1, SECONDS);

        // assert
        verify(amazonSqsAsync, times(1)).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(messageRetrieved).isEqualTo(message);
    }

    @Test
    public void consumerThatIsInterruptedStillAllowsRetrieverToStopWhenWaitingForConsumersToTakeMessaeg() throws Exception {
        // arrange
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder().build();
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                Executors.newCachedThreadPool(), properties);
        final Message message = new Message();
        batchingMessageRetriever.start();
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        final CountDownLatch consumerTimedOutLatch = new CountDownLatch(1);
        when(amazonSqsAsync.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receivedMessageLatch.countDown();
            consumerTimedOutLatch.await();
            return new ReceiveMessageResult()
                    .withMessages(message);
        });
        final Future<?> retrieveMessageFuture = obtainFutureOnBackgroundThreadWhenInterrupted(batchingMessageRetriever, consumerTimedOutLatch);

        // act
        receivedMessageLatch.await(POLLING_PERIOD_IN_MS * 2, TimeUnit.MILLISECONDS);
        retrieveMessageFuture.cancel(true);
        consumerTimedOutLatch.await(1000, TimeUnit.MILLISECONDS);
        log.info("Obtained message");
        batchingMessageRetriever.stop().get(1, SECONDS);

        // assert
        verify(amazonSqsAsync, times(1)).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
    }

    @Test
    public void errorObtainingMessagesWillTryAgainAfterPollingPeriodExpiresAgain() throws Exception {
        // arrange
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                Executors.newCachedThreadPool(), DEFAULT_PROPERTIES);
        final long startTime = System.currentTimeMillis();
        batchingMessageRetriever.start();
        final Message message = new Message();
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(amazonSqsAsync.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new RuntimeException("error"))
                .thenAnswer(invocation -> {
                    receivedMessageLatch.countDown();
                    return new ReceiveMessageResult()
                            .withMessages(message);
                });

        // act
        final Message messageRetrieved = batchingMessageRetriever.retrieveMessage();
        final long actualTime = System.currentTimeMillis() - startTime;
        batchingMessageRetriever.stop().get(1, SECONDS);

        // assert
        verify(amazonSqsAsync, times(2)).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(messageRetrieved).isEqualTo(message);
        assertThat(actualTime).isGreaterThanOrEqualTo(POLLING_PERIOD_IN_MS * 2);
    }

    @Test
    public void willNotExceedAwsMaxMessagesForRetrieval() throws Exception {
        // arrange
        final StaticBatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder().numberOfThreadsWaitingTrigger(10).build();
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, amazonSqsAsync,
                Executors.newCachedThreadPool(), properties);
        batchingMessageRetriever.start();
        final Message message = new Message();
        final CountDownLatch receivedMessageLatch = new CountDownLatch(1);
        when(amazonSqsAsync.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(invocation -> {
                    receivedMessageLatch.countDown();
                    return new ReceiveMessageResult()
                            .withMessages(message);
                });

        // act
        final Set<? extends Future<?>> allMessagesFutures = IntStream.range(0, 12)
                .mapToObj(i -> requestMessageOnNewThread(batchingMessageRetriever))
                .collect(Collectors.toSet());
        receivedMessageLatch.await(1, SECONDS);
        batchingMessageRetriever.stop().get(1, SECONDS);
        allMessagesFutures.forEach(future -> future.cancel(true));

        // assert
        verify(amazonSqsAsync).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().getMaxNumberOfMessages()).isEqualTo(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
    }

    private Future<?> requestMessageOnNewThread(final BatchingMessageRetriever batchingMessageRetriever) {
        return Executors.newCachedThreadPool().submit(() -> {
            try {
                batchingMessageRetriever.retrieveMessage();
            } catch (InterruptedException interruptedException) {
                throw new RuntimeException(interruptedException);
            }
        });
    }

    private Future<?> obtainFutureOnBackgroundThreadWhenInterrupted(final BatchingMessageRetriever batchingMessageRetriever,
                                                                    final CountDownLatch interruptedCountdownLatch) {
        return Executors.newCachedThreadPool().submit(() -> {
            try {
                batchingMessageRetriever.retrieveMessage();
            } catch (InterruptedException interruptedException) {
                interruptedCountdownLatch.countDown();
            }
        });
    }
}