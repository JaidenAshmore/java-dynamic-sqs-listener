package com.jashmore.sqs.retriever.prefetch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.isA;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.jashmore.sqs.QueueProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;

@Slf4j
public class PrefetchingMessageRetrieverTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl(QUEUE_URL)
            .build();

    private static final PrefetchingProperties DEFAULT_PREFETCHING_PROPERTIES = PrefetchingProperties.builder()
            .desiredMinPrefetchedMessages(10)
            .maxPrefetchedMessages(20)
            .maxNumberOfMessagesToObtainFromServer(10)
            .maxWaitTimeInSecondsToObtainMessagesFromServer(2)
            .visibilityTimeoutForMessagesInSeconds(10)
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private AmazonSQSAsync amazonSqsAsync;

    private ExecutorService executorService = Executors.newCachedThreadPool();

    @Test
    public void whenNoMessagesPresentRetrieveMessageIsBlocked() throws InterruptedException {
        // arrange
        final CountDownLatch messagesRequested = new CountDownLatch(1);
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch);
        when(amazonSqsAsync.receiveMessage(any(ReceiveMessageRequest.class)))
                .then(waitUntilTestCompleted(messagesRequested, testCompletedLatch));
        prefetchingMessageRetriever.start();
        messagesRequested.await(1, SECONDS);

        try {
            // act
            final Optional<Message> optionalMessage = prefetchingMessageRetriever.retrieveMessage(50, MILLISECONDS);

            // assert
            assertThat(optionalMessage).isEmpty();
        } finally {
            prefetchingMessageRetriever.stop();
        }
    }

    @Test
    public void messageObtainedFromServerCanBeObtained() throws InterruptedException {
        // arrange
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final CountDownLatch secondMessageRequestedLatched = new CountDownLatch(1);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch);
        final Message messageFromSqs = new Message().withBody("test");
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(new ReceiveMessageResult().withMessages(messageFromSqs)))
                .thenAnswer(waitUntilTestCompleted(secondMessageRequestedLatched, testCompletedLatch));
        prefetchingMessageRetriever.start();
        secondMessageRequestedLatched.await(1, SECONDS);

        // act
        try {
            final Optional<Message> optionalFirstMessage = prefetchingMessageRetriever.retrieveMessage(1, SECONDS);

            // assert
            assertThat(optionalFirstMessage).contains(messageFromSqs);
        } finally {
            prefetchingMessageRetriever.stop();
        }
    }

    @Test
    public void messageObtainedFromServerCanBeObtainedButNextIsBlockedAsQueueIsEmpty() throws InterruptedException {
        // arrange
        final CountDownLatch testFinishedCountDownLatch = new CountDownLatch(1);
        final CountDownLatch requestedSecondMessageLatch = new CountDownLatch(1);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testFinishedCountDownLatch);
        final Message messageFromSqs = new Message().withBody("test");
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(new ReceiveMessageResult().withMessages(messageFromSqs)))
                .thenAnswer(waitUntilTestCompleted(requestedSecondMessageLatch, testFinishedCountDownLatch));
        prefetchingMessageRetriever.start();

        // act
        final Optional<Message> optionalFirstMessage = prefetchingMessageRetriever.retrieveMessage(1, SECONDS);
        requestedSecondMessageLatch.await(1, SECONDS);
        try {
            final Optional<Message> secondMessageAttempted = prefetchingMessageRetriever.retrieveMessage(200, TimeUnit.MILLISECONDS);

            // assert
            assertThat(optionalFirstMessage).contains(messageFromSqs);
            assertThat(secondMessageAttempted).isEmpty();
        } finally {
            prefetchingMessageRetriever.stop();
        }
    }

    @Test
    public void multipleMessagesCanBePulledFromSingleRequestAndObtainedInOrder() throws InterruptedException {
        // arrange
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final CountDownLatch secondmessagesRequested = new CountDownLatch(1);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch);
        final Message firstMessage = new Message().withBody("test");
        final Message secondMessage = new Message().withBody("test");
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(new ReceiveMessageResult().withMessages(firstMessage, secondMessage)))
                .thenAnswer(waitUntilTestCompleted(secondmessagesRequested, testCompletedLatch));
        prefetchingMessageRetriever.start();
        secondmessagesRequested.await(1, SECONDS);

        try {
            // act
            final Optional<Message> optionalFirstMessage = prefetchingMessageRetriever.retrieveMessage(1, SECONDS);
            final Optional<Message> optionalSecondMessage = prefetchingMessageRetriever.retrieveMessage(1, SECONDS);

            // assert
            assertThat(optionalFirstMessage).contains(firstMessage);
            assertThat(optionalSecondMessage).contains(secondMessage);
        } finally {
            prefetchingMessageRetriever.stop();
        }
    }

    @Test
    public void messageRetrievalAreBlockedUntilResponseFromAwsReturns() throws InterruptedException {
        // arrange
        final CountDownLatch testCompleted = new CountDownLatch(1);
        final CountDownLatch messagesRetrievalLatch = new CountDownLatch(1);
        final CountDownLatch messageRequestedLatch = new CountDownLatch(1);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(amazonSqsAsync, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES, executorService) {
            @Override
            public Optional<Message> retrieveMessage(final long timeout, @NotNull final TimeUnit timeUnit) throws InterruptedException {
                // Wait until we have sent the request to get some messages from AWS
                messagesRetrievalLatch.await();
                // We have requested the message so we can now have AWS return the message
                messageRequestedLatch.countDown();
                return super.retrieveMessage(timeout, timeUnit);
            }
        };
        final Message message = new Message().withBody("test");
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer((Answer<Future<ReceiveMessageResult>>) invocation -> {
                    messagesRetrievalLatch.countDown();
                    // Wait until we are requesting messages
                    messageRequestedLatch.await();
                    return mockFutureWithGet(new ReceiveMessageResult().withMessages(message));
                })
                .thenAnswer(invocation -> {
                    testCompleted.await(1, SECONDS);
                    return mockFutureWithGet(new ReceiveMessageResult());
                });

        // act
        prefetchingMessageRetriever.start();
        try {
            final Optional<Message> optionalMessageRetrieved = prefetchingMessageRetriever.retrieveMessage(1, SECONDS);

            // assert
            assertThat(optionalMessageRetrieved).contains(message);
        } finally {
            prefetchingMessageRetriever.stop();
        }
    }

    @Test
    public void willGetMessagesAcrossMultiplePrefetchingRequestsIfMoreSpaceInInternalQueue() throws InterruptedException {
        // arrange
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final CountDownLatch twoMessagesRetrieved = new CountDownLatch(1);
        final PrefetchingProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxPrefetchedMessages(4)
                .desiredMinPrefetchedMessages(2)
                .build();
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch, prefetchingProperties);
        final Message firstRequestMessage = new Message();
        final Message secondRequestMessage = new Message();
        final Message thirdMessage = new Message();
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(new ReceiveMessageResult().withMessages(firstRequestMessage)))
                .thenAnswer(invocation -> {
                    twoMessagesRetrieved.countDown();
                    return mockFutureWithGet(new ReceiveMessageResult().withMessages(secondRequestMessage, thirdMessage));
                })
                .thenAnswer((invocationOnMock) -> {
                    throw new IllegalStateException("This shouldn't get called cause we hit our prefetch limit");
                });

        // act
        prefetchingMessageRetriever.start();
        twoMessagesRetrieved.await(200, MILLISECONDS);
        prefetchingMessageRetriever.stop();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(amazonSqsAsync, times(2)).receiveMessageAsync(requestArgumentCaptor.capture());
        assertThat(requestArgumentCaptor.getAllValues().get(0).getMaxNumberOfMessages()).isEqualTo(4);
        assertThat(requestArgumentCaptor.getAllValues().get(1).getMaxNumberOfMessages()).isEqualTo(3);
    }

    @Test
    public void whenMinDesiredAndMaxSameItWillRetrieveNewMessagesAsSoonAsOneIsConsumed() throws InterruptedException {
        // arrange
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final CountDownLatch secondMessageRequestedLatch = new CountDownLatch(1);
        final CountDownLatch finalMessageRequestedLatch = new CountDownLatch(1);
        final PrefetchingProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(5)
                .maxNumberOfMessagesToObtainFromServer(10)
                .maxPrefetchedMessages(5)
                .build();
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch, prefetchingProperties);
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(new ReceiveMessageResult()
                        .withMessages(new Message(), new Message(), new Message(), new Message(), new Message())))
                .thenAnswer((invocationOnMock) -> {
                    secondMessageRequestedLatch.countDown();
                    return mockFutureWithGet(new ReceiveMessageResult().withMessages(new Message()));
                })
                .thenAnswer(waitUntilTestCompleted(finalMessageRequestedLatch, testCompletedLatch));
        prefetchingMessageRetriever.start();

        // act
        Thread.sleep(500); // As the retriever's thread will be blocked we will wait to make sure it hasn't made a second request for messages
        verify(amazonSqsAsync, times(1)).receiveMessageAsync(any(ReceiveMessageRequest.class));
        assertThat(secondMessageRequestedLatch.getCount()).isEqualTo(1);

        try {
            prefetchingMessageRetriever.retrieveMessage(1, SECONDS);
            // Now that a message has been consumed the retriever should request for new messages to hit the maxPrefetchedMessages
            secondMessageRequestedLatch.await(1, SECONDS);
        } finally {
            prefetchingMessageRetriever.stop();
        }

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(amazonSqsAsync, times(2)).receiveMessageAsync(requestArgumentCaptor.capture());
        assertThat(requestArgumentCaptor.getAllValues().get(0).getMaxNumberOfMessages()).isEqualTo(5);
        assertThat(requestArgumentCaptor.getAllValues().get(1).getMaxNumberOfMessages()).isEqualTo(1);
    }

    @Test
    public void willAttemptToGetAllPrefetchMessagesOnFirstCall() throws InterruptedException {
        // arrange
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final CountDownLatch messagesReceivedLatch = new CountDownLatch(1);
        final PrefetchingProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(4)
                .maxNumberOfMessagesToObtainFromServer(10)
                .maxPrefetchedMessages(5)
                .build();
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch, prefetchingProperties);
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer(invocation -> {
                    messagesReceivedLatch.countDown();
                    return mockFutureWithGet(new ReceiveMessageResult().withMessages(new Message(), new Message(), new Message(), new Message(), new Message()));
                })
                .thenAnswer((invocationOnMock) -> {
                    throw new IllegalStateException("This shouldn't get called cause we hit our prefetch limit");
                });

        // act
        prefetchingMessageRetriever.start();
        messagesReceivedLatch.await(1, SECONDS);
        prefetchingMessageRetriever.stop();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(amazonSqsAsync, times(1)).receiveMessageAsync(requestArgumentCaptor.capture());
        assertThat(requestArgumentCaptor.getAllValues().get(0).getMaxNumberOfMessages()).isEqualTo(5);
    }

    /**
     * This is a performance requirement in that we don't want requests for more messages to occur every single time a message is consumed, resulting
     * in lots of requests for single messages. This will make sure that it will try and collect multiple messages from the queue.
     */
    @Test
    public void willNotRequestMoreMessagesUntilFirstPrefetchPlacesAllMessagesOntoQueue() throws InterruptedException {
        // arrange
        final CountDownLatch firstmessagesRequested = new CountDownLatch(1);
        final CountDownLatch secondmessagesRequested = new CountDownLatch(1);
        final CountDownLatch thirdmessagesRequested = new CountDownLatch(1);
        final PrefetchingProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(2)
                .maxPrefetchedMessages(4)
                .maxNumberOfMessagesToObtainFromServer(10)
                .build();
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer(invocation -> {
                    firstmessagesRequested.countDown();
                    return mockFutureWithGet(new ReceiveMessageResult().withMessages(new Message(), new Message()));
                })
                .thenAnswer(invocation -> {
                    secondmessagesRequested.countDown();
                    return mockFutureWithGet(new ReceiveMessageResult().withMessages(new Message()));
                })
                .thenAnswer((invocationOnMock) -> {
                    thirdmessagesRequested.countDown();
                    return mockFutureWithGet(new ReceiveMessageResult().withMessages(new Message()));
                });
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(amazonSqsAsync, QUEUE_PROPERTIES,
                prefetchingProperties, executorService);
        prefetchingMessageRetriever.start();

        // act
        firstmessagesRequested.await(1, SECONDS);
        Thread.sleep(1000); // Make sure we haven't called the second message
        assertThat(secondmessagesRequested.getCount()).isEqualTo(1L);
        prefetchingMessageRetriever.retrieveMessage(1, SECONDS);
        secondmessagesRequested.await(1, SECONDS);
        prefetchingMessageRetriever.retrieveMessage(1, SECONDS);
        thirdmessagesRequested.await(1, SECONDS);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(amazonSqsAsync, times(3)).receiveMessageAsync(requestArgumentCaptor.capture());
        assertThat(requestArgumentCaptor.getAllValues().get(0).getMaxNumberOfMessages()).isEqualTo(4);
        assertThat(requestArgumentCaptor.getAllValues().get(1).getMaxNumberOfMessages()).isEqualTo(3);
        assertThat(requestArgumentCaptor.getAllValues().get(2).getMaxNumberOfMessages()).isEqualTo(3);
    }

    @Test
    public void noExceptionThrownForStoppingWhenAlreadyStopped() {
        // arrange
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(amazonSqsAsync, QUEUE_PROPERTIES, DEFAULT_PREFETCHING_PROPERTIES,
                executorService);

        // act
        prefetchingMessageRetriever.stop();
    }

    @Test
    public void cannotStartWhenRetrieverHasAlreadyStarted() {
        // arrange
        final CountDownLatch messagesRequestedLatch = new CountDownLatch(1);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(amazonSqsAsync, QUEUE_PROPERTIES,
                DEFAULT_PREFETCHING_PROPERTIES, executorService);
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenAnswer((invocation -> {
            messagesRequestedLatch.await();
            return mockFutureWithGet(new ReceiveMessageResult());
        }));
        prefetchingMessageRetriever.start();
        expectedException.expect(isA(IllegalStateException.class));
        expectedException.expectMessage("PrefetchingMessageRetriever is already running");

        // act
        prefetchingMessageRetriever.start();
    }

    @Test
    public void stoppingAfterRetrieverHasAlreadyStoppedDoesNotThrowException() throws Exception {
        // arrange
        final CountDownLatch stopRequestedLatch = new CountDownLatch(1);
        final CountDownLatch messagesRequestedLatch = new CountDownLatch(1);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(stopRequestedLatch);
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenAnswer((invocation -> {
            messagesRequestedLatch.countDown();
            stopRequestedLatch.await();
            return mockFutureWithGet(new ReceiveMessageResult());
        }));
        prefetchingMessageRetriever.start();
        messagesRequestedLatch.await(1, SECONDS);
        prefetchingMessageRetriever.stop();

        // act
        prefetchingMessageRetriever.stop();
    }

    /**
     * This test is making sure that we don't sit and wait to place messages in the internal queue forever. It will attempt to place a message on a queue for
     * a certain period, during this time the retriever is stopped and this makes sure that it will end the process when this happens.
     */
    @Test
    public void stoppingRetrievalWhilstPlacingOnTheQueueCanExitWhenTheQueueDoesNotAcceptMessage() throws InterruptedException {
        // arrange
        final CountDownLatch messagesReceivedLatch = new CountDownLatch(1);
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final PrefetchingProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxPrefetchedMessages(2)
                .desiredMinPrefetchedMessages(2)
                .build();
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch, prefetchingProperties);
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenAnswer((invocation -> {
            messagesReceivedLatch.countDown();
            return mockFutureWithGet(new ReceiveMessageResult().withMessages(new Message(), new Message()));
        }));
        prefetchingMessageRetriever.start();

        // act
        messagesReceivedLatch.await(1, TimeUnit.SECONDS);
        prefetchingMessageRetriever.stop();

        // assert
        verify(amazonSqsAsync, times(1)).receiveMessageAsync(any(ReceiveMessageRequest.class));
    }

    /**
     * This makes sure that if {@link PrefetchingProperties#desiredMinPrefetchedMessages} is set to zero it will not attempt to call out for more messages
     * until there are no messages left in the retriever.
     */
    @Test
    public void prefetchWithDesiredMinimumPrefetchedMessagesAsZeroWorksAsIntended() throws InterruptedException {
        // arrange
        final CountDownLatch secondPrefetchRequested = new CountDownLatch(1);
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final PrefetchingProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxPrefetchedMessages(2)
                .desiredMinPrefetchedMessages(0)
                .build();
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch, prefetchingProperties);
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(new ReceiveMessageResult().withMessages(new Message(), new Message())))
                .thenAnswer(waitUntilTestCompleted(secondPrefetchRequested, testCompletedLatch));

        try {
            // act
            prefetchingMessageRetriever.start();

            // assert
            prefetchingMessageRetriever.retrieveMessage(1, SECONDS);
            verify(amazonSqsAsync, times(1)).receiveMessageAsync(any(ReceiveMessageRequest.class));
            prefetchingMessageRetriever.retrieveMessage(1, SECONDS);
            secondPrefetchRequested.await(1, SECONDS);
            verify(amazonSqsAsync, times(2)).receiveMessageAsync(any(ReceiveMessageRequest.class));
        } finally {
            prefetchingMessageRetriever.stop();
        }
    }

    @Test
    public void exceptionThrownRetrievingMessagesDoesNotStopBackgroundThread() throws InterruptedException {
        // arrange
        final CountDownLatch secondPrefetchRequested = new CountDownLatch(1);
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final PrefetchingProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .maxPrefetchedMessages(2)
                .desiredMinPrefetchedMessages(0)
                .errorBackoffTimeInMilliseconds(0)
                .build();
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch, prefetchingProperties);
        final Message expectedMessage = new Message();
        when(amazonSqsAsync.receiveMessageAsync(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureThatThrows(new RuntimeException("error")))
                .thenAnswer(answerWithMockFutureWithGet(new ReceiveMessageResult().withMessages(expectedMessage)))
                .thenAnswer(waitUntilTestCompleted(secondPrefetchRequested, testCompletedLatch));

        try {
            // act
            prefetchingMessageRetriever.start();
            final Optional<Message> message = prefetchingMessageRetriever.retrieveMessage(1, SECONDS);

            // assert
            assertThat(message).contains(expectedMessage);
        } finally {
            prefetchingMessageRetriever.stop();
        }
    }

    private PrefetchingMessageRetriever buildRetriever(final CountDownLatch testCompletedLatch) {
        return buildRetriever(testCompletedLatch, DEFAULT_PREFETCHING_PROPERTIES);
    }

    private PrefetchingMessageRetriever buildRetriever(final CountDownLatch testCompletedLatch, final PrefetchingProperties prefetchingProperties) {
        return new PrefetchingMessageRetriever(amazonSqsAsync, QUEUE_PROPERTIES, prefetchingProperties, executorService) {
            @Override
            public synchronized Future<?> stop() {
                try {
                    return super.stop();
                } finally {
                    testCompletedLatch.countDown();
                }
            }
        };
    }

    private <T> Answer<Future<T>> answerWithMockFutureWithGet(final T result) {
        return (invocation) -> mockFutureWithGet(result);
    }

    @SuppressWarnings("unchecked")
    private <T> Answer<Future<T>> answerWithMockFutureThatThrows(final Throwable throwable) {
        return (invocation -> {
            final Future<T> future = mock(Future.class);
            doThrow(new ExecutionException(throwable)).when(future).get();
            return future;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> Future<T> mockFutureWithGet(final T result) {
        final Future<T> future = mock(Future.class);
        try {
            when(future.get()).thenReturn(result);
        } catch (InterruptedException | ExecutionException e) {
            // ignore
        }
        return future;
    }

    private Answer<Future<ReceiveMessageResult>> waitUntilTestCompleted(final CountDownLatch messageRequestedLatch, final CountDownLatch testCompletedLatch) {
        return (invocation) -> {
            messageRequestedLatch.countDown();
            testCompletedLatch.await(1, SECONDS);
            return mockFutureWithGet(new ReceiveMessageResult());
        };
    }
}
