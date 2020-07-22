package com.jashmore.sqs.retriever.prefetch;

import static com.jashmore.sqs.util.thread.ThreadTestUtils.startRunnableInThread;
import static com.jashmore.sqs.util.thread.ThreadTestUtils.waitUntilThreadInState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
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
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@ExtendWith(MockitoExtension.class)
class PrefetchingMessageRetrieverTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl(QUEUE_URL)
            .build();

    private static final StaticPrefetchingMessageRetrieverProperties DEFAULT_PREFETCHING_PROPERTIES = StaticPrefetchingMessageRetrieverProperties.builder()
            .desiredMinPrefetchedMessages(1)
            .maxPrefetchedMessages(2)
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
    void desiredPrefetchedMessagesGreaterThanMaxPrefetchedMessagesThrowsException() {
        // arrange
        final PrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
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
        final PrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
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
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, message));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxPrefetchedMessages(2)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final List<Message> leftOverMessages = runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        assertThat(leftOverMessages).containsExactly(message);
    }

    @Test
    void backgroundThreadWillRequestAsManyPrefetchedMessagesAsNeeded() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxPrefetchedMessages(2)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(2);
    }

    @Test
    void multipleMessagesCanBeObtainedFromMultipleRequestsAndPlacedIntoInternalQueue() {
        final Message firstBatchMessageOne = Message.builder().build();
        final Message firstBatchMessageTwo = Message.builder().build();
        final Message secondBatchMessageOne = Message.builder().build();
        final CountDownLatch receiveMessageRequested = new CountDownLatch(2);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, firstBatchMessageOne, firstBatchMessageTwo))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, secondBatchMessageOne));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(3)
                .maxPrefetchedMessages(4)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final List<Message> leftOverMessages = runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        assertThat(leftOverMessages).containsExactly(firstBatchMessageOne, firstBatchMessageTwo, secondBatchMessageOne);
    }

    @Test
    void whenThereAreAlreadyPrefetchedMessagesItWillRequestUpToMaxBatchSize() {
        final Message firstBatchMessageOne = Message.builder().build();
        final Message firstBatchMessageTwo = Message.builder().build();
        final Message secondBatchMessageOne = Message.builder().build();
        final CountDownLatch receiveMessageRequested = new CountDownLatch(2);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, firstBatchMessageOne, firstBatchMessageTwo))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, secondBatchMessageOne));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(3)
                .maxPrefetchedMessages(4)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient, times(2)).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getAllValues().get(1).maxNumberOfMessages()).isEqualTo(2);
    }

    @Test
    void backgroundThreadWillNotTryToRequestMoreMessagesThanTheAwsMaximum() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxPrefetchedMessages(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
    }

    @Test
    void whenInternalMessageQueueIsAboveTheDesiredMinPrefetchedMessagesTheThreadWaitsUntilTheQueueDecreasesBelowMinimum() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(2);
        final Message firstMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, firstMessage))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(1)
                .maxPrefetchedMessages(2)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        startRunnableInThread(retriever::run, thread -> {
            // act
            waitUntilThreadInState(thread, Thread.State.WAITING);
            retriever.retrieveMessage();

            // assert
            assertThat(receiveMessageRequested.await(30, TimeUnit.SECONDS)).isTrue();
        });
    }

    @Test
    void whenDesiredPrefetchedIsSameAsMaximumItWillNeverExceedDesiredMessages() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        final Message firstMessage = Message.builder().build();
        final Message secondMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, firstMessage, secondMessage));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(2)
                .maxPrefetchedMessages(2)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        startRunnableInThread(retriever::run, thread -> {
            // act
            waitUntilThreadInState(thread, Thread.State.WAITING);

            // assert
            assertThat(receiveMessageRequested.await(30, TimeUnit.SECONDS)).isTrue();
            verify(sqsAsyncClient).receiveMessage(any(ReceiveMessageRequest.class));
        });
    }

    @Test
    void whenDesiredMinPrefetchedIssuesIsTwoItWillCorrectlyTriggerForNewMessagesWhenThereIsOnlyOnePrefetchedInternally() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(2);
        final Message firstMessage = Message.builder().build();
        final Message secondMessage = Message.builder().build();
        final Message thirdMessage = Message.builder().build();
        final Message fourthMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, firstMessage, secondMessage, thirdMessage))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, fourthMessage));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(2)
                .maxPrefetchedMessages(3)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        startRunnableInThread(retriever::run, thread -> {
            // act
            waitUntilThreadInState(thread, Thread.State.WAITING);
            retriever.retrieveMessage();
            waitUntilThreadInState(thread, Thread.State.WAITING);
            verify(sqsAsyncClient).receiveMessage(any(ReceiveMessageRequest.class));
            retriever.retrieveMessage();

            // assert
            assertThat(receiveMessageRequested.await(30, TimeUnit.SECONDS)).isTrue();
            verify(sqsAsyncClient, times(2)).receiveMessage(any(ReceiveMessageRequest.class));
        });
    }

    @Test
    void waitTimeForPrefetchingPropertiesWillBeSqsMaximum() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(1)
                .maxPrefetchedMessages(2)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds()).isEqualTo(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }

    @Test
    void whenNoMessageVisibilityTimeoutIncludedTheVisibilityTimeoutIsNullInReceiveMessageRequest() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .messageVisibilityTimeout(null)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    void whenNegativeMessageVisibilityTimeoutIncludedTheVisibilityTimeoutIsNullInReceiveMessageRequest() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .messageVisibilityTimeout(Duration.ofSeconds(-1))
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    void whenZeroMessageVisibilityTimeoutIncludedTheVisibilityTimeoutIsNullInReceiveMessageRequest() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .messageVisibilityTimeout(Duration.ZERO)
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    void whenMessageVisibilityTimeoutIncludedTheVisibilityTimeoutIsIncludedInReceiveMessageRequest() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .messageVisibilityTimeout(Duration.ofSeconds(2))
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isEqualTo(2);
    }

    @Test
    void allMessageAttributesAreIncludedInMessagesWhenRetrieved() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().messageAttributeNames()).containsExactly(QueueAttributeName.ALL.toString());
    }

    @Test
    void allMessageSystemAttributesAreIncludedInMessagesWhenRetrieved() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES);

        // act
        runRetrieverUntilLatch(retriever, receiveMessageRequested);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().attributeNames()).contains(QueueAttributeName.ALL);
    }

    @Test
    void threadInterruptedPuttingMessageOnTheQueueWillNotRequestAnyMoreMessages() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES);

        startRunnableInThread(retriever::run, thread -> {
            // act
            receiveMessageRequested.await(30, TimeUnit.SECONDS);
            waitUntilThreadInState(thread, Thread.State.WAITING);
            thread.interrupt();

            // assert
            waitUntilThreadInState(thread, Thread.State.TERMINATED);
        });
    }

    @Test
    void exceptionThrownObtainingMessagesWillBackOffForSpecifiedPeriod() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new ExpectedTestException())
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final int backoffTimeInMilliseconds = 500;
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTime(Duration.ofMillis(backoffTimeInMilliseconds))
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final long startTime = System.currentTimeMillis();
        runRetrieverUntilLatch(retriever, receiveMessageRequested);
        final long endTime = System.currentTimeMillis();

        // assert
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(backoffTimeInMilliseconds);
    }

    @Test
    void threadInterruptedWhileBackingOffWillStopBackgroundThread() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new ExpectedTestException());
        final int backoffTimeInMilliseconds = 500;
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTime(Duration.ofMillis(backoffTimeInMilliseconds))
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        startRunnableInThread(retriever::run, thread -> {
            waitUntilThreadInState(thread, Thread.State.TIMED_WAITING);

            // assert
            thread.interrupt();
            waitUntilThreadInState(thread, Thread.State.TERMINATED);
        });
    }

    @Test
    void rejectedFutureFromSqsAsyncClientWillBackoff() {
        // arrange
        final CountDownLatch receiveMessageRequested = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()))
                .thenAnswer(triggerLatchAndReturnMessages(receiveMessageRequested, Message.builder().build()));
        final int backoffTimeInMilliseconds = 500;
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTime(Duration.ofMillis(backoffTimeInMilliseconds))
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final long startTime = System.currentTimeMillis();
        runRetrieverUntilLatch(retriever, receiveMessageRequested);
        final long endTime = System.currentTimeMillis();

        // assert
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(backoffTimeInMilliseconds);
    }

    @Test
    void whenSqsAsyncClientThrowsSdkInterruptedExceptionTheBackgroundThreadShouldStop() {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFutureUtils.completedExceptionally(SdkClientException.builder().cause(new SdkInterruptedException()).build()));
        final int backoffTimeInMilliseconds = 500;
        final StaticPrefetchingMessageRetrieverProperties properties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .errorBackoffTime(Duration.ofMillis(backoffTimeInMilliseconds))
                .build();
        final PrefetchingMessageRetriever retriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        startRunnableInThread(retriever::run, thread -> {
            // assert
            waitUntilThreadInState(thread, Thread.State.TERMINATED);
        });
    }

    private List<Message> runRetrieverUntilLatch(final PrefetchingMessageRetriever retriever, final CountDownLatch latch) {
        try {
            final CompletableFuture<List<Message>> future = CompletableFuture.supplyAsync(retriever::run, executorService);
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(1000); // Give us a little bit of extra time just in case
            executorService.shutdownNow();
            executorService.awaitTermination(30, TimeUnit.SECONDS);
            return future.get(30, TimeUnit.SECONDS);
        } catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private Answer<CompletableFuture<ReceiveMessageResponse>> triggerLatchAndReturnMessages(final CountDownLatch latch, final Message... messages) {
        return invocation -> {
            latch.countDown();
            return mockReceiveMessageResponse(messages);
        };
    }

    private CompletableFuture<ReceiveMessageResponse> mockReceiveMessageResponse(final Message... messages) {
        return CompletableFuture.completedFuture(ReceiveMessageResponse.builder()
                .messages(messages)
                .build());
    }
}
