package com.jashmore.sqs.retriever.batching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class BatchingMessageRetrieverTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl(QUEUE_URL)
            .build();

    private static final long POLLING_PERIOD_IN_MS = 50L;
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
    private CompletableFuture<ReceiveMessageResponse> responseThrowingInterruptedException;

    @Mock
    private CompletableFuture<ReceiveMessageResponse> responseThrowingException;

    @Before
    public void setUp() {
        responseThrowingException(responseThrowingInterruptedException, new InterruptedException());
        responseThrowingException(responseThrowingException, new RuntimeException("Expected test exception"));
    }

    @Test
    public void interruptedExceptionWhileWaitingForMessagesWillStopBackgroundThread() {
        // arrange
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, DEFAULT_PROPERTIES) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };

        // act
        backgroundThread.run();

        // assert
        verify(sqsAsyncClient, never()).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void whenThereAreMoreThreadsRequestMessagesThanTheThresholdItWillRequestMessagesStraightAway() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .numberOfThreadsWaitingTrigger(2)
                .build();
        final int threadsCurrentlyRequestingMessages = 2;
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(
                QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties, new AtomicInteger(threadsCurrentlyRequestingMessages), new LinkedBlockingQueue<>(),
                new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(2);
    }

    @Test
    public void whenThePollingPeriodIsHitTheBackgroundThreadWillRequestAsManyMessagesAsThoseWaiting() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .numberOfThreadsWaitingTrigger(2)
                .build();
        final int threadsRequestingMessages = 1;
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            private boolean requestedOnce = false;

            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                if (!requestedOnce) {
                    requestedOnce = true;
                } else {
                    throw new InterruptedException();
                }
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse());

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(1);
    }

    @Test
    public void whenThereAreAlreadyBatchedMessagesItWillOnlyRequestTheExtraMessagesNeeded() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .numberOfThreadsWaitingTrigger(2)
                .build();
        final int threadsRequestingMessages = 3;
        final LinkedBlockingQueue<Message> internalMessageQueue = new LinkedBlockingQueue<>(ImmutableList.of(Message.builder().build()));
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(threadsRequestingMessages), internalMessageQueue, new Object()) {

            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(2);
    }

    @Test
    public void whenNoVisibilityTimeoutIncludedTheReceiveMessageRequestWillIncludeNullVisibilityTimeout() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .visibilityTimeoutInSeconds(null)
                .build();
        final int threadsRequestingMessages = DEFAULT_PROPERTIES.getNumberOfThreadsWaitingTrigger();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    public void whenNegativeVisibilityTimeoutIncludedTheReceiveMessageRequestWillIncludeNullVisibilityTimeout() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .visibilityTimeoutInSeconds(-1)
                .build();
        final int threadsRequestingMessages = DEFAULT_PROPERTIES.getNumberOfThreadsWaitingTrigger();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(
                QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    public void whenValidVisibilityTimeoutIncludedTheReceiveMessageRequestWillIncludeThisVisibilityTimeout() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .visibilityTimeoutInSeconds(5)
                .build();
        final int threadsRequestingMessages = DEFAULT_PROPERTIES.getNumberOfThreadsWaitingTrigger();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(
                QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isEqualTo(5);
    }

    @Test
    public void errorObtainingMessagesWillTryAgainAfterBackingOffPeriod() {
        // arrange
        final int currentThreadsRequestMessages = 1;
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(200L)
                .numberOfThreadsWaitingTrigger(1)
                .build();
        final AtomicLong actualBackoffTime = new AtomicLong(-1);
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(currentThreadsRequestMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }

            @Override
            void backoff(final long backoffTimeInMs) throws InterruptedException {
                actualBackoffTime.set(backoffTimeInMs);
                super.backoff(backoffTimeInMs);
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingException)
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // act
        assertThat(actualBackoffTime).hasValue(200L);
        verify(sqsAsyncClient, times(2)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void interruptedExceptionThrownWhenBackingOffWillEndBackgroundThread() {
        // arrange
        final int currentThreadsRequestMessages = 1;
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .numberOfThreadsWaitingTrigger(1)
                .build();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(currentThreadsRequestMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void backoff(final long backoffTimeInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingException);

        // act
        backgroundThread.run();

        // act
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void willNotExceedAwsMaxMessagesForRetrievalWhenRequestingMessages() {
        // arrange
        final int currentThreadsRequestMessages = 11;
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(200L)
                .numberOfThreadsWaitingTrigger(11)
                .build();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(currentThreadsRequestMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // act
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
    }

    @Test
    public void waitTimeProvidedInPropertiesIsUsedForMessageRetrievalRequests() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .waitTimeInSeconds(10)
                .build();
        final int threadsRequestingMessages = DEFAULT_PROPERTIES.getNumberOfThreadsWaitingTrigger();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds()).isEqualTo(10);
    }

    @Test
    public void negativeWaitTimeProvidedInPropertiesWillUseMaxWaitTimeInsteadForMessageRetrievalRequests() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .waitTimeInSeconds(-1)
                .build();
        final int threadsRequestingMessages = DEFAULT_PROPERTIES.getNumberOfThreadsWaitingTrigger();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds()).isEqualTo(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }

    @Test
    public void waitTimeProvidedInPropertiesGreaterThanAwsMaximumWillUseMaxWaitTimeInsteadForMessageRetrievalRequests() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .waitTimeInSeconds(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS + 1)
                .build();
        final int threadsRequestingMessages = DEFAULT_PROPERTIES.getNumberOfThreadsWaitingTrigger();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds()).isEqualTo(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }

    @Test
    public void allMessageAttributesShouldBeDownloadedWhenRequestingMessages() {
        // arrange
        final int threadsRequestingMessages = DEFAULT_PROPERTIES.getNumberOfThreadsWaitingTrigger();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                DEFAULT_PROPERTIES, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(Message.builder().build()));

        // act
        backgroundThread.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().messageAttributeNames()).containsExactly(QueueAttributeName.ALL.toString());
    }

    @Test
    public void nullPollingPeriodWillWaitUntilEnoughThreadsAreRequestingMessages() {
        // arrange
        final AtomicLong actualWaitTimeInMs = new AtomicLong(-1);
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder()
                .messageRetrievalPollingPeriodInMs(null)
                .build();
        final int threadsRequestingMessages = 0;
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                retrieverProperties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
                actualWaitTimeInMs.set(waitPeriodInMs);
                throw new InterruptedException();
            }
        };

        // act
        backgroundThread.run();

        // assert
        assertThat(actualWaitTimeInMs).hasValue(0L);
    }

    @Test
    public void onErrorReceivingMessageWhenErrorBackoffTimeIncludedThatIsUsedForBackoff() {
        // arrange
        final AtomicLong actualBackOffTimeInMs = new AtomicLong(-1);
        final StaticBatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(10L)
                .build();
        final int threadsRequestingMessages = properties.getNumberOfThreadsWaitingTrigger();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                properties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void backoff(final long backoffTimeInMs) {
                actualBackOffTimeInMs.set(backoffTimeInMs);
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingException)
                .thenReturn(responseThrowingInterruptedException);

        // act
        backgroundThread.run();

        // assert
        assertThat(actualBackOffTimeInMs).hasValue(10L);
    }

    @Test
    public void whenNoErrorBackoffTimeUsedTheDefaultIsSupplied() {
        // arrange
        final AtomicLong actualBackOffTimeInMs = new AtomicLong(-1);
        final StaticBatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(null)
                .build();
        final int threadsRequestingMessages = properties.getNumberOfThreadsWaitingTrigger();
        final BatchingMessageRetriever backgroundThread = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient,
                properties, new AtomicInteger(threadsRequestingMessages), new LinkedBlockingQueue<>(), new Object()) {
            @Override
            void backoff(final long backoffTimeInMs) {
                actualBackOffTimeInMs.set(backoffTimeInMs);
            }
        };
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(responseThrowingException)
                .thenReturn(responseThrowingInterruptedException);

        // act
        backgroundThread.run();

        // assert
        assertThat(actualBackOffTimeInMs).hasValue(BatchingMessageRetrieverConstants.DEFAULT_BACKOFF_TIME_IN_MS);
    }

    @Test
    public void whenEnoughThreadsRequestMessagesTheRetrievalOfMessagesWillTrigger() throws Exception {
        // arrange
        final BatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .messageRetrievalPollingPeriodInMs(null) // Null will wait forever
                .numberOfThreadsWaitingTrigger(1)
                .build();
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final BatchingMessageRetriever batchingMessageRetriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, properties);
        final Future<?> retrieverOnBackgroundThread = executorService.submit(batchingMessageRetriever);
        final Message message = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(mockReceiveMessageResponse(message));
        final Future<Message> retrieveMessageFuture = executorService.submit(batchingMessageRetriever::retrieveMessage);

        // act
        final Message actualMessage = retrieveMessageFuture.get();
        retrieverOnBackgroundThread.cancel(true);

        // assert
        assertThat(actualMessage).isEqualTo(message);
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
