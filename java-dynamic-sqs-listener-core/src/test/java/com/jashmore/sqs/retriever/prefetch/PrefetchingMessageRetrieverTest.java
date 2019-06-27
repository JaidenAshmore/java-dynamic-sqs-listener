package com.jashmore.sqs.retriever.prefetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkInterruptedException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
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
            .messageVisibilityTimeoutInSeconds(10)
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private LinkedBlockingQueue<Message> mockLinkedBlockingQueue;

    @Mock
    private CompletableFuture<ReceiveMessageResponse> responseThrowingInterruptedException;

    @Mock
    private CompletableFuture<ReceiveMessageResponse> responseThrowingException;

    private ExecutorService executorService;

    @Before
    public void setUp() {
        responseThrowingException(responseThrowingInterruptedException, new InterruptedException());
        responseThrowingException(responseThrowingException, new RuntimeException("Expected test exception"));

        executorService = Executors.newCachedThreadPool();
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
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
        new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);
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
        new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);
    }

    @Test
    public void messageObtainedFromServerCanBeObtained() {
        // arrange
        final Message message = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(message))
                .thenReturn(responseThrowingInterruptedException);
        final LinkedBlockingQueue<Message> internalMessageQueue = new LinkedBlockingQueue<>();
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
    }

    @Test
    public void waitTimeForPrefetchingPropertiesWillBeSQSMaximum() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout())
                .isEqualTo(DEFAULT_PREFETCHING_PROPERTIES.getMessageVisibilityTimeoutInSeconds());
    }

    @Test
    public void allMessageAttributesAreIncludedInMessagesWhenRetrieved() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().messageAttributeNames()).contains(QueueAttributeName.ALL.toString());
    }

    @Test
    public void allMessageSystemAttributesAreIncludedInMessagesWhenRetrieved() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().attributeNames()).contains(QueueAttributeName.ALL);
    }

    @Test
    public void whenNoMessageVisibilityTimeoutFromPrefetchingPropertiesIncludedTheAwsMaximumWaitingTimeIsUsed() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetrieverProperties prefetchingMessageRetrieverProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .messageVisibilityTimeoutInSeconds(null)
                .build();
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingMessageRetrieverProperties,
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
                .messageVisibilityTimeoutInSeconds(-1)
                .build();
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingMessageRetrieverProperties,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    public void zeroMessageVisibilityTimeoutWillNotIncludeVisibilityTimeout() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingInterruptedException);
        final PrefetchingMessageRetrieverProperties prefetchingMessageRetrieverProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .messageVisibilityTimeoutInSeconds(0)
                .build();
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingMessageRetrieverProperties,
                new LinkedBlockingQueue<>(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    public void threadInterruptedPuttingMessageOnTheQueueWillNotRequestAnyMoreMessages() throws Exception {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties,
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
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties,
                mockLinkedBlockingQueue, AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);

        // act
        final int backoffTimeInMs = backgroundMessagePrefetcher.getBackoffTimeInMs();

        // assert
        assertThat(backoffTimeInMs).isEqualTo(0);
    }

    @Test
    public void whenInternalMessageQueueIsAboveTheDesiredMinPrefetchedMessagesTheThreadWaitsUntilTheQueueDecreasesBelowTheMinimum()
            throws InterruptedException {
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
                prefetchingProperties);
        executorService.submit(prefetchingMessageRetriever);

        // act
        prefetchingMessageRetriever.retrieveMessage();
        prefetchingMessageRetriever.retrieveMessage();
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        prefetchingMessageRetriever.retrieveMessage();

        // assert
        verify(sqsAsyncClient, timeout(1000).times(2)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void whenDesiredMinPrefetchedIssuesIsTwoItWillCorrectlyTriggerForNewMessagesWhenThereIsOnlyOnePrefetchedInternally()
            throws InterruptedException {
        // arrange
        final StaticPrefetchingMessageRetrieverProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(2)
                .maxPrefetchedMessages(3)
                .build();
        final Message firstMessage = Message.builder().build();
        final Message secondMessage = Message.builder().build();
        final Message thirdMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(firstMessage, secondMessage, thirdMessage));
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                prefetchingProperties);
        executorService.submit(prefetchingMessageRetriever);

        // act
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
                .thenReturn(mockReceiveMessageResponse(firstMessage, secondMessage, thirdMessage))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                prefetchingProperties);
        executorService.submit(prefetchingMessageRetriever);

        // act
        prefetchingMessageRetriever.retrieveMessage();

        // assert
        verify(sqsAsyncClient, timeout(1000).times(2)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void stoppingRetrieverViaInterruptedWillEndBackgroundThread() throws InterruptedException, TimeoutException, ExecutionException {
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
                DEFAULT_PREFETCHING_PROPERTIES);
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        final Future<?> retrieverBackgroundThread = executorService.submit(() -> {
            prefetchingMessageRetriever.run();
            completableFuture.complete("done");
        });
        requestedMessagesLatch.await(1, TimeUnit.SECONDS);

        // act
        retrieverBackgroundThread.cancel(true);
        waitingForFutureStopping.countDown();

        // assert
        completableFuture.get(2, TimeUnit.SECONDS);
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void cancellingRetrieverDuringBackoffWillNotRequestMoreMessages() throws Exception {
        // arrange
        final int backoffTimeInMilliseconds = 4_000;
        final PrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(backoffTimeInMilliseconds)
                .build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingException);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                properties);
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        final Future<?> retrieverBackgroundThread = executorService.submit(() -> {
            prefetchingMessageRetriever.run();
            completableFuture.complete("done");
        });

        // act
        Thread.sleep(backoffTimeInMilliseconds / 2);
        retrieverBackgroundThread.cancel(true);

        // assert
        completableFuture.get();
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void whenSqsAsyncClientThrowsSdkInterruptedExceptionTheBackgroundThreadShouldStop() throws Exception {
        // arrange
        final LinkedBlockingQueue<Message> internalMessageQueue = new LinkedBlockingQueue<>();
        internalMessageQueue.add(Message.builder().build());
        doThrow(new ExecutionException(SdkClientException.builder().cause(new SdkInterruptedException()).build())).when(responseThrowingException).get();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingException);
        final PrefetchingMessageRetriever backgroundMessagePrefetcher
                = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                internalMessageQueue, 5);

        // act
        backgroundMessagePrefetcher.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
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
