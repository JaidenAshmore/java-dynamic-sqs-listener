package com.jashmore.sqs.retriever.prefetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.isA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class PrefetchingMessageRetrieverTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl(QUEUE_URL)
            .build();

    private static final StaticPrefetchingMessageRetrieverProperties DEFAULT_PREFETCHING_PROPERTIES = StaticPrefetchingMessageRetrieverProperties.builder()
            .desiredMinPrefetchedMessages(10)
            .maxPrefetchedMessages(20)
            .maxWaitTimeInSecondsToObtainMessagesFromServer(2)
            .visibilityTimeoutForMessagesInSeconds(10)
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private ExecutorService mockExecutorService;

    @Mock
    private Future<?> mockFuture;

    @Mock
    private LinkedBlockingQueue<Message> mockLinkedBlockingQueue;

    @Mock
    private CompletableFuture<ReceiveMessageResponse> responseThrowingInterruptedException;

    @Mock
    private CompletableFuture<ReceiveMessageResponse> responseThrowingException;

    private ExecutorService executorService = Executors.newCachedThreadPool();

    @Before
    public void setUp() {
        responseThrowingException(responseThrowingInterruptedException, new InterruptedException());
        responseThrowingException(responseThrowingException, new RuntimeException("Expected test exception"));
    }

    @Test
    public void lessMaxPrefetchedMessagesThanDesiredPrefetchedThrowsException() {
        // arrange
        final PrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(10)
                .maxPrefetchedMessages(5)
                .build();
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("maxPrefetchedMessages should be greater than or equal to desiredMinPrefetchedMessages");

        // act
        new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties, executorService);
    }

    @Test
    public void desiredMinPrefetchedMessagesLessThanOneThrowsErrorInConstruction() {
        // arrange
        final PrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(0)
                .maxPrefetchedMessages(5)
                .build();
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("desiredMinPrefetchedMessages must be greater than zero");

        // act
        new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties, executorService);
    }

    @Test
    public void noExceptionThrownForStoppingWhenItHasNotBeenStartedYet() {
        // arrange
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                DEFAULT_PREFETCHING_PROPERTIES, executorService);

        // act
        final Future<Object> stoppingFuture = prefetchingMessageRetriever.stop();

        // assert
        assertThat(stoppingFuture).isDone();
    }

    @Test
    public void cannotStartWhenRetrieverHasAlreadyStarted() {
        // arrange
        doReturn(mockFuture).when(mockExecutorService).submit(any(Runnable.class));
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                DEFAULT_PREFETCHING_PROPERTIES, mockExecutorService);
        prefetchingMessageRetriever.start();
        expectedException.expect(isA(IllegalStateException.class));
        expectedException.expectMessage("PrefetchingMessageRetriever is already running");

        // act
        prefetchingMessageRetriever.start();
    }

    @Test
    public void stoppingAfterRetrieverHasAlreadyStoppedDoesNotThrowException() {
        // arrange
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                DEFAULT_PREFETCHING_PROPERTIES, mockExecutorService);
        prefetchingMessageRetriever.start();
        prefetchingMessageRetriever.stop();

        // act
        final Future<Object> stoppingFuture = prefetchingMessageRetriever.stop();

        // assert
        assertThat(stoppingFuture).isDone();
    }

    @Test
    public void messageObtainedFromServerCanBeObtained() {
        // arrange
        final Message message = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(message))
                .thenReturn(responseThrowingInterruptedException);
        final LinkedBlockingQueue<Message> internalMessageQueue = new LinkedBlockingQueue<>();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                internalMessageQueue, 10);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        assertThat(internalMessageQueue).hasSize(1);
        assertThat(internalMessageQueue).contains(message);
    }

    @Test
    public void multipleMessagesCanBeObtainedFromMultipleRequestsAndPlacedIntoInternalQueue() {
        final Message firstBatchMessageOne = Message.builder().build();
        final Message firstBatchMessageTwo = Message.builder().build();
        final Message secondBatchMessageOne = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(firstBatchMessageOne, firstBatchMessageTwo))
                .thenReturn(mockReceiveMessageResponse(secondBatchMessageOne))
                .thenReturn(responseThrowingInterruptedException);
        final LinkedBlockingQueue<Message> internalMessageQueue = new LinkedBlockingQueue<>();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                internalMessageQueue, 10);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        assertThat(internalMessageQueue).hasSize(3);
        assertThat(internalMessageQueue).containsExactly(firstBatchMessageOne, firstBatchMessageTwo, secondBatchMessageOne);
    }

    @Test
    public void backgroundThreadWillRequestMessagesFromTheProvidedQueueUrl() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                new LinkedBlockingQueue<>(), 5);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().queueUrl()).isEqualTo(QUEUE_PROPERTIES.getQueueUrl());
    }

    @Test
    public void backgroundThreadWillRequestAsManyPrefetchedMessagesAsNeeded() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                new LinkedBlockingQueue<>(), 5);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(5);
    }

    @Test
    public void whenThereAreAnyPrefetchedMessagesItWillOnlyRequestTheNumberToGetToMaxPrefetchedMessages() {
        // arrange
        final LinkedBlockingQueue<Message> internalMessageQueue = new LinkedBlockingQueue<>();
        internalMessageQueue.add(Message.builder().build());
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                internalMessageQueue, 5);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(4);
    }

    @Test
    public void backgroundThreadWillNotTryToRequestMoreMessagesThanTheAwsMaximum() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
    }

    @Test
    public void waitTimeFromPrefetchingPropertiesIncludedInReceiveMessageRequest() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds())
                .isEqualTo(DEFAULT_PREFETCHING_PROPERTIES.getMessageWaitTimeInSeconds());
    }

    @Test
    public void whenNoWaitTimeFromPrefetchingPropertiesIncludedTheAwsMaximumWaitingTimeIsUsed() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetrieverProperties prefetchingMessageRetrieverProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxWaitTimeInSecondsToObtainMessagesFromServer(null)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingMessageRetrieverProperties,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds()).isEqualTo(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }

    @Test
    public void negativeWaitTimeForPrefetchingPropertiesWillUseTheAwsMaximumWaitingTimeInstead() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetrieverProperties prefetchingMessageRetrieverProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxWaitTimeInSecondsToObtainMessagesFromServer(-1)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingMessageRetrieverProperties,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds()).isEqualTo(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }

    @Test
    public void waitTimeForPrefetchingPropertiesGreaterThanAwsMaximumWillUseTheAwsMaximumWaitingTimeInstead() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetrieverProperties prefetchingMessageRetrieverProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxWaitTimeInSecondsToObtainMessagesFromServer(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS + 1)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingMessageRetrieverProperties,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds()).isEqualTo(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }


    @Test
    public void messageVisibilityTimeoutFromPrefetchingPropertiesIncludedInReceiveMessageRequest() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout())
                .isEqualTo(DEFAULT_PREFETCHING_PROPERTIES.getVisibilityTimeoutForMessagesInSeconds());
    }

    @Test
    public void whenNoMessageVisibilityTimeoutFromPrefetchingPropertiesIncludedTheAwsMaximumWaitingTimeIsUsed() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetrieverProperties prefetchingMessageRetrieverProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .visibilityTimeoutForMessagesInSeconds(null)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingMessageRetrieverProperties,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    public void negativeMessageVisibilityTimeoutForPrefetchingPropertiesWillUseTheAwsMaximumWaitingTimeInstead() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetrieverProperties prefetchingMessageRetrieverProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .visibilityTimeoutForMessagesInSeconds(-1)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingMessageRetrieverProperties,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    public void zeroMessageVisibilityTimeoutForPrefetchingPropertiesIsValidVisibilityTimeout() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetrieverProperties prefetchingMessageRetrieverProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .visibilityTimeoutForMessagesInSeconds(0)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingMessageRetrieverProperties,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isEqualTo(0);
    }

    @Test
    public void threadInterruptedPuttingMessageOnTheQueueWillNotRequestAnyMoreMessages() throws Exception {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                mockLinkedBlockingQueue, AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
        doThrow(new InterruptedException()).when(mockLinkedBlockingQueue).put(any(Message.class));

        // act
        backgroundMessagePrefetcher.run();

        // assert
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void exceptionThrownObtainingMessagesWillBackOffForSpecifiedPeriod() {
        // arrange
        final AtomicLong firstMessageRequestTime = new AtomicLong();
        final AtomicLong secondMessageRequestTime = new AtomicLong();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer((invocation) -> {
                    firstMessageRequestTime.set(System.currentTimeMillis());
                    return responseThrowingException;
                })
                .thenAnswer((invocation) -> {
                    secondMessageRequestTime.set(System.currentTimeMillis());
                    return responseThrowingInterruptedException;
                });
        final int backoffTimeInMilliseconds = 500;
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(backoffTimeInMilliseconds)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, properties,
                mockLinkedBlockingQueue, AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        assertThat(secondMessageRequestTime.get() - firstMessageRequestTime.get()).isGreaterThanOrEqualTo(backoffTimeInMilliseconds);
    }

    @Test
    public void whenBackoffTimeIsNullTheDefaultIsUsedForTheBackoffPeriod() {
        // arrange
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(null)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, properties,
                mockLinkedBlockingQueue, AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);

        // act
        final int backoffTimeInMs = backgroundMessagePrefetcher.getBackoffTimeInMs();

        // assert
        assertThat(backoffTimeInMs).isEqualTo(PrefetchingMessageRetrieverConstants.DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS);
    }

    @Test
    public void whenBackoffTimeIsNegativeTheDefaultIsUsedForTheBackoffPeriod() {
        // arrange
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(-5)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, properties,
                mockLinkedBlockingQueue, AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);

        // act
        final int backoffTimeInMs = backgroundMessagePrefetcher.getBackoffTimeInMs();

        // assert
        assertThat(backoffTimeInMs).isEqualTo(PrefetchingMessageRetrieverConstants.DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS);
    }

    @Test
    public void whenBackoffTimeIsZeroTheBackoffTimeOfZeroMillisecondsIsUsed() {
        // arrange
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(0)
                .build();
        final PrefetchingMessageRetriever.BackgroundMessagePrefetcher backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever.BackgroundMessagePrefetcher(sqsAsyncClient, QUEUE_PROPERTIES, properties,
                mockLinkedBlockingQueue, AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);

        // act
        final int backoffTimeInMs = backgroundMessagePrefetcher.getBackoffTimeInMs();

        // assert
        assertThat(backoffTimeInMs).isEqualTo(0);
    }

    @Test
    public void whenInternalMessageQueueIsAboveTheDesiredMinPrefetchedMessagesTheThreadWaitsUntilTheQueueDecreasesBelowTheMinimum() throws InterruptedException {
        // arrange
        final StaticPrefetchingMessageRetrieverProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(1)
                .maxPrefetchedMessages(3)
                .build();
        final Message firstMessage = Message.builder().build();
        final Message secondMessage = Message.builder().build();
        final Message thirdMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(firstMessage, secondMessage, thirdMessage));
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                prefetchingProperties, executorService);
        prefetchingMessageRetriever.start();

        // act
        prefetchingMessageRetriever.retrieveMessage();
        prefetchingMessageRetriever.retrieveMessage();
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        prefetchingMessageRetriever.retrieveMessage();

        // assert
        verify(sqsAsyncClient, timeout(1000).times(2)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void whenMinDesiredAndMaxSameAreTheSameTheRetrieverWIllPrefetchNewMessagesAsSoonAsOneIsConsumed() throws InterruptedException {
        // arrange
        final StaticPrefetchingMessageRetrieverProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(3)
                .maxPrefetchedMessages(3)
                .build();
        final Message firstMessage = Message.builder().build();
        final Message secondMessage = Message.builder().build();
        final Message thirdMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(firstMessage, secondMessage, thirdMessage));
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                prefetchingProperties, executorService);
        prefetchingMessageRetriever.start();

        // act
        prefetchingMessageRetriever.retrieveMessage();

        // assert
        verify(sqsAsyncClient, timeout(1000).times(2)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void stoppingRetrieverWillEndBackgroundThreadResolvingStopFuture() throws InterruptedException, TimeoutException, ExecutionException {
        // arrange
        final CountDownLatch requestedMessagesLatch = new CountDownLatch(1);
        final CountDownLatch waitingForFutureStopping = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer((invocationOnMock) -> {
                    requestedMessagesLatch.countDown();
                    waitingForFutureStopping.await();
                    return mockReceiveMessageResponse();
                });
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                DEFAULT_PREFETCHING_PROPERTIES, executorService);
        prefetchingMessageRetriever.start();
        requestedMessagesLatch.await(1, TimeUnit.SECONDS);

        // act
        final Future<Object> future = prefetchingMessageRetriever.stop();
        waitingForFutureStopping.countDown();

        // assert
        future.get(2, TimeUnit.SECONDS);
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void cancellingRetrieverDuringBackoffWillNotRequestMoreMessages() throws Exception {
        // arrange
        final int backoffTimeInMilliseconds = 2_000;
        final PrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(backoffTimeInMilliseconds)
                .build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingException);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                properties, executorService);
        prefetchingMessageRetriever.start();

        // act
        Thread.sleep(backoffTimeInMilliseconds / 2);
        final Future<Object> stopFuture = prefetchingMessageRetriever.stop();

        // assert
        stopFuture.get();
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    private void responseThrowingException(final CompletableFuture<ReceiveMessageResponse> mockResponse,
                                           final Throwable throwable) {
        try {
            when(mockResponse.get()).thenThrow(throwable);
        } catch (final InterruptedException | ExecutionException exception) {
            throw new RuntimeException(exception);
        }
    }

    private CompletableFuture<ReceiveMessageResponse> mockReceiveMessageResponse(final Message... messages) {
        return mockFutureWithGet(ReceiveMessageResponse.builder()
                .messages(messages)
                .build());
    }

    private <T> CompletableFuture<T> mockFutureWithGet(final T result) {
        return CompletableFuture.completedFuture(result);
    }
}
