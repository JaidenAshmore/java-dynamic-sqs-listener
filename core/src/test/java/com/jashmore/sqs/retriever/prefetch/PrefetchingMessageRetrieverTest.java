package com.jashmore.sqs.retriever.prefetch;

import static com.jashmore.sqs.util.thread.ThreadTestUtils.startRunnableInThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkInterruptedException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@Slf4j
@ExtendWith(MockitoExtension.class)
class PrefetchingMessageRetrieverTest {

    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder().queueUrl(QUEUE_URL).build();

    private static final CompletableFuture<ReceiveMessageResponse> RECEIVE_MESSAGES_INTERRUPTED =
        CompletableFutureUtils.completedExceptionally(SdkClientException.builder().cause(new SdkInterruptedException()).build());

    private static final StaticPrefetchingMessageRetrieverProperties DEFAULT_PREFETCHING_PROPERTIES =
        StaticPrefetchingMessageRetrieverProperties.builder().desiredMinPrefetchedMessages(1).maxPrefetchedMessages(2).build();

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
    void desiredPrefetchedMessagesGreaterThanMaxPrefetchedMessagesThrowsException() {
        // arrange
        final PrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .desiredMinPrefetchedMessages(10)
            .maxPrefetchedMessages(5)
            .build();

        // act
        final IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties)
        );

        // assert
        assertThat(exception).hasMessage("maxPrefetchedMessages should be greater than or equal to desiredMinPrefetchedMessages");
    }

    @Test
    void desiredMinPrefetchedMessagesLessThanOneThrowsErrorInConstruction() {
        // arrange
        final PrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .desiredMinPrefetchedMessages(0)
            .maxPrefetchedMessages(5)
            .build();

        // act
        final IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties)
        );

        // assert
        assertThat(exception).hasMessage("desiredMinPrefetchedMessages must be greater than zero");
    }

    @Test
    void whenStartedMessagesWillBeRequestedForPrefetchedMessages() {
        // arrange
        final Message message = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(mockReceiveMessageResponse(message))
            .thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .desiredMinPrefetchedMessages(2)
            .maxPrefetchedMessages(2)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final List<Message> leftOverMessages = retriever.run();

        // assert
        assertThat(leftOverMessages).containsExactly(message);
    }

    @Test
    void backgroundThreadWillRequestAsManyPrefetchedMessagesAsNeeded() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .maxPrefetchedMessages(2)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(2);
    }

    @Test
    void multipleMessagesCanBeObtainedFromMultipleRequestsAndPlacedIntoInternalQueue() {
        final Message firstBatchMessageOne = Message.builder().build();
        final Message firstBatchMessageTwo = Message.builder().build();
        final Message secondBatchMessageOne = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(mockReceiveMessageResponse(firstBatchMessageOne, firstBatchMessageTwo))
            .thenReturn(mockReceiveMessageResponse(secondBatchMessageOne))
            .thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .desiredMinPrefetchedMessages(4)
            .maxPrefetchedMessages(5)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final List<Message> leftOverMessages = retriever.run();

        // assert
        assertThat(leftOverMessages).containsExactly(firstBatchMessageOne, firstBatchMessageTwo, secondBatchMessageOne);
    }

    @Test
    void whenThereAreAlreadyPrefetchedMessagesItWillRequestUpToMaxBatchSize() {
        final Message firstBatchMessageOne = Message.builder().build();
        final Message firstBatchMessageTwo = Message.builder().build();
        final Message secondBatchMessageOne = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(mockReceiveMessageResponse(firstBatchMessageOne, firstBatchMessageTwo))
            .thenReturn(mockReceiveMessageResponse(secondBatchMessageOne))
            .thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .desiredMinPrefetchedMessages(4)
            .maxPrefetchedMessages(5)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient, times(3)).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getAllValues().get(0).maxNumberOfMessages()).isEqualTo(5);
        assertThat(receiveMessageRequestArgumentCaptor.getAllValues().get(1).maxNumberOfMessages()).isEqualTo(3);
    }

    @Test
    void backgroundThreadWillNotTryToRequestMoreMessagesThanTheAwsMaximum() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .maxPrefetchedMessages(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages())
            .isEqualTo(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
    }

    @Test
    void whenDesiredPrefetchedIsSameAsMaximumItWillNeverExceedDesiredMessages() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        final Message firstMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, firstMessage));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .desiredMinPrefetchedMessages(1)
            .maxPrefetchedMessages(1)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        startRunnableInThread(
            retriever::run,
            thread -> {
                // act
                assertThat(receiveMessageRequested.await(5, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(500); // Wait a little bit to make sure that we have messages

                // assert
                verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
            }
        );
    }

    @Test
    void whenAMessageIsTakenToPutThePrefetchedCountBelowDesiredNewMessagesWillBeFetched() throws Exception {
        // arrange
        final CountDownLatch firstMessagesFetchedLatch = new CountDownLatch(1);
        final CountDownLatch secondMessageGroupRequestedLatch = new CountDownLatch(1);
        final Message firstMessage = Message.builder().build();
        final Message secondMessage = Message.builder().build();
        final Message thirdMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(triggerLatchAndReturnMessages(firstMessagesFetchedLatch, firstMessage, secondMessage))
            .thenAnswer(triggerLatchAndReturnMessages(secondMessageGroupRequestedLatch, thirdMessage))
            .thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .desiredMinPrefetchedMessages(2)
            .maxPrefetchedMessages(3)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final Future<List<Message>> future = Executors.newSingleThreadExecutor().submit(retriever::run);
        assertThat(firstMessagesFetchedLatch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100); // wait a little bit to make sure that we have attempted to put messages onto the queue
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        assertThat(retriever.retrieveMessage()).isCompletedWithValue(firstMessage);

        // assert
        assertThat(secondMessageGroupRequestedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(retriever.retrieveMessage()).isCompletedWithValue(secondMessage);
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void waitTimeForPrefetchingPropertiesWillBeSqsMaximum() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .desiredMinPrefetchedMessages(1)
            .maxPrefetchedMessages(2)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds())
            .isEqualTo(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }

    @Test
    void whenNoMessageVisibilityTimeoutIncludedTheVisibilityTimeoutIsNullInReceiveMessageRequest() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .messageVisibilityTimeout(null)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    void whenNegativeMessageVisibilityTimeoutIncludedTheVisibilityTimeoutIsNullInReceiveMessageRequest() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .messageVisibilityTimeout(Duration.ofSeconds(-1))
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    void whenZeroMessageVisibilityTimeoutIncludedTheVisibilityTimeoutIsNullInReceiveMessageRequest() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .messageVisibilityTimeout(Duration.ZERO)
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    void whenMessageVisibilityTimeoutIncludedTheVisibilityTimeoutIsIncludedInReceiveMessageRequest() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .messageVisibilityTimeout(Duration.ofSeconds(2))
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isEqualTo(2);
    }

    @Test
    void allMessageAttributesAreIncludedInMessagesWhenRetrieved() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(
            sqsAsyncClient,
            QUEUE_PROPERTIES,
            DEFAULT_PREFETCHING_PROPERTIES
        );

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().messageAttributeNames()).contains(QueueAttributeName.ALL.toString());
    }

    @Test
    void allMessageSystemAttributesAreIncludedInMessagesWhenRetrieved() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(
            sqsAsyncClient,
            QUEUE_PROPERTIES,
            DEFAULT_PREFETCHING_PROPERTIES
        );

        // act
        retriever.run();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().messageSystemAttributeNames()).contains(MessageSystemAttributeName.ALL);
    }

    @Test
    void threadInterruptedPuttingMessageOnTheQueueWillNotRequestAnyMoreMessages() throws InterruptedException {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build(), Message.builder().build()));
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(
            sqsAsyncClient,
            QUEUE_PROPERTIES,
            DEFAULT_PREFETCHING_PROPERTIES
        );

        // act
        final Future<List<Message>> future = Executors.newSingleThreadExecutor().submit(retriever::run);
        receiveMessageRequested.await(5, TimeUnit.SECONDS);
        Thread.sleep(100); // Sleep a little bit to make sure we are blocked by the internal message queue
        future.cancel(true);

        // assert
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void exceptionThrownObtainingMessagesWillBackOffForSpecifiedPeriod() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenThrow(new ExpectedTestException())
            .thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final int backoffTimeInMilliseconds = 500;
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .errorBackoffTime(Duration.ofMillis(backoffTimeInMilliseconds))
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final long startTime = System.currentTimeMillis();
        retriever.run();
        final long endTime = System.currentTimeMillis();

        // assert
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(backoffTimeInMilliseconds);
    }

    @Test
    void threadInterruptedWhileBackingOffWillStopBackgroundThread() {
        // arrange
        final AtomicBoolean backoffOccurred = new AtomicBoolean(false);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenThrow(new ExpectedTestException());
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(
            sqsAsyncClient,
            QUEUE_PROPERTIES,
            new PrefetchingMessageRetrieverProperties() {
                @Override
                public @Positive int getDesiredMinPrefetchedMessages() {
                    return 2;
                }

                @Override
                public @Positive int getMaxPrefetchedMessages() {
                    return 4;
                }

                @Override
                public @Nullable @Positive Duration getMessageVisibilityTimeout() {
                    return null;
                }

                @Override
                public @Nullable @PositiveOrZero Duration getErrorBackoffTime() {
                    backoffOccurred.set(true);
                    Thread.currentThread().interrupt();
                    return Duration.ofMillis(500);
                }
            }
        );

        // act
        retriever.run();

        // assert
        assertThat(backoffOccurred.get()).isTrue();
    }

    @Test
    void rejectedFutureFromSqsAsyncClientWillBackoff() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()))
            .thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final int backoffTimeInMilliseconds = 500;
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .errorBackoffTime(Duration.ofMillis(backoffTimeInMilliseconds))
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final long startTime = System.currentTimeMillis();
        retriever.run();
        final long endTime = System.currentTimeMillis();

        // assert
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(backoffTimeInMilliseconds);
    }

    @Test
    void whenSqsAsyncClientThrowsSdkInterruptedExceptionTheBackgroundThreadShouldStop() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(RECEIVE_MESSAGES_INTERRUPTED);
        final int backoffTimeInMilliseconds = 500;
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES
            .toBuilder()
            .errorBackoffTime(Duration.ofMillis(backoffTimeInMilliseconds))
            .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.run();
    }

    private Answer<CompletableFuture<ReceiveMessageResponse>> triggerLatchAndReturnMessages(
        final CountDownLatch latch,
        final Message... messages
    ) {
        return invocation -> {
            latch.countDown();
            return mockReceiveMessageResponse(messages);
        };
    }

    private CompletableFuture<ReceiveMessageResponse> mockReceiveMessageResponse(final Message... messages) {
        return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(messages).build());
    }
}
