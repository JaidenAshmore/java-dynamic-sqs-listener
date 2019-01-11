package com.jashmore.sqs.retriever.prefetch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.hamcrest.core.Is.isA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class PrefetchingMessageRetrieverTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl(QUEUE_URL)
            .build();

    private static final PrefetchingProperties DEFAULT_PREFETCHING_PROPERTIES = PrefetchingProperties.builder()
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

    private ExecutorService executorService = Executors.newCachedThreadPool();

    @Test
    public void messageObtainedFromServerCanBeObtained() throws InterruptedException {
        // arrange
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final CountDownLatch secondMessageRequestedLatched = new CountDownLatch(1);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch);
        final Message messageFromSqs = Message.builder().body("test").build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(ReceiveMessageResponse.builder().messages(messageFromSqs).build()))
                .thenAnswer(waitUntilTestCompleted(secondMessageRequestedLatched, testCompletedLatch));
        prefetchingMessageRetriever.start();
        secondMessageRequestedLatched.await(1, SECONDS);

        // act
        try {
            final Message optionalFirstMessage = prefetchingMessageRetriever.retrieveMessage();

            // assert
            assertThat(optionalFirstMessage).isEqualTo(messageFromSqs);
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
        final Message messageFromSqs = Message.builder().body("test").build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(ReceiveMessageResponse.builder().messages(messageFromSqs).build()))
                .thenAnswer(waitUntilTestCompleted(requestedSecondMessageLatch, testFinishedCountDownLatch));
        prefetchingMessageRetriever.start();

        // act
        final Message optionalFirstMessage = prefetchingMessageRetriever.retrieveMessage();
        assertThat(optionalFirstMessage).isEqualTo(messageFromSqs);
        try {
            final Future<?> messageWaitingFuture = Executors.newCachedThreadPool().submit(() -> {
                try {
                    prefetchingMessageRetriever.retrieveMessage();
                } catch (InterruptedException interruptedException) {
                    // should be interrupted
                }
            });

            // assert
            try {
                try {
                    messageWaitingFuture.get(100, MILLISECONDS);
                } catch (TimeoutException timeoutException) {
                    messageWaitingFuture.cancel(true);
                    try {
                        messageWaitingFuture.get();
                        fail("Should have cancelled the future");
                    } catch (CancellationException cancellationException) {
                        // expected
                    }

                }
            } catch (ExecutionException executionException) {
                throw new RuntimeException(executionException);
            }
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
        final Message firstMessage = Message.builder().body("test").build();
        final Message secondMessage = Message.builder().body("test").build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(ReceiveMessageResponse.builder().messages(firstMessage, secondMessage).build()))
                .thenAnswer(waitUntilTestCompleted(secondmessagesRequested, testCompletedLatch));
        prefetchingMessageRetriever.start();
        secondmessagesRequested.await(1, SECONDS);

        try {
            // act
            final Message optionalFirstMessage = prefetchingMessageRetriever.retrieveMessage();
            final Message optionalSecondMessage = prefetchingMessageRetriever.retrieveMessage();

            // assert
            assertThat(optionalFirstMessage).isEqualTo(firstMessage);
            assertThat(optionalSecondMessage).isEqualTo(secondMessage);
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
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                DEFAULT_PREFETCHING_PROPERTIES, executorService) {
            @Override
            public Message retrieveMessage() throws InterruptedException {
                // Wait until we have sent the request to get some messages from AWS
                messagesRetrievalLatch.await();
                // We have requested the message so we can now have AWS return the message
                messageRequestedLatch.countDown();
                return super.retrieveMessage();
            }
        };
        final Message message = Message.builder().body("test").build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer((Answer<Future<ReceiveMessageResponse>>) invocation -> {
                    messagesRetrievalLatch.countDown();
                    // Wait until we are requesting messages
                    messageRequestedLatch.await();
                    return mockFutureWithGet(ReceiveMessageResponse.builder().messages(message).build());
                })
                .thenAnswer(invocation -> {
                    testCompleted.await(1, SECONDS);
                    return mockFutureWithGet(ReceiveMessageResponse.builder());
                });

        // act
        prefetchingMessageRetriever.start();
        try {
            final Message messageRetrieved = prefetchingMessageRetriever.retrieveMessage();

            // assert
            assertThat(messageRetrieved).isEqualTo(message);
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
        final Message firstRequestMessage = Message.builder().build();
        final Message secondRequestMessage = Message.builder().build();
        final Message thirdMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(ReceiveMessageResponse.builder().messages(firstRequestMessage).build()))
                .thenAnswer(invocation -> {
                    twoMessagesRetrieved.countDown();
                    return mockFutureWithGet(ReceiveMessageResponse.builder().messages(secondRequestMessage, thirdMessage).build());
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
        verify(sqsAsyncClient, times(2)).receiveMessage(requestArgumentCaptor.capture());
        assertThat(requestArgumentCaptor.getAllValues().get(0).maxNumberOfMessages()).isEqualTo(4);
        assertThat(requestArgumentCaptor.getAllValues().get(1).maxNumberOfMessages()).isEqualTo(3);
    }

    @Test
    public void whenMinDesiredAndMaxSameItWillRetrieveNewMessagesAsSoonAsOneIsConsumed() throws InterruptedException {
        // arrange
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final CountDownLatch secondMessageRequestedLatch = new CountDownLatch(1);
        final CountDownLatch finalMessageRequestedLatch = new CountDownLatch(1);
        final PrefetchingProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(5)
                .maxPrefetchedMessages(5)
                .build();
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch, prefetchingProperties);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(ReceiveMessageResponse.builder()
                        .messages(
                                Message.builder().build(), Message.builder().build(),
                                Message.builder().build(), Message.builder().build(),
                                Message.builder().build())
                        .build()))
                .thenAnswer((invocationOnMock) -> {
                    secondMessageRequestedLatch.countDown();
                    return mockFutureWithGet(ReceiveMessageResponse.builder().messages(Message.builder().build()));
                })
                .thenAnswer(waitUntilTestCompleted(finalMessageRequestedLatch, testCompletedLatch));
        prefetchingMessageRetriever.start();

        // act
        Thread.sleep(500); // As the retriever's thread will be blocked we will wait to make sure it hasn't made a second request for messages
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        assertThat(secondMessageRequestedLatch.getCount()).isEqualTo(1);

        try {
            prefetchingMessageRetriever.retrieveMessage();
            // Now that a message has been consumed the retriever should request for new messages to hit the maxPrefetchedMessages
            secondMessageRequestedLatch.await(1, SECONDS);
        } finally {
            prefetchingMessageRetriever.stop();
        }

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient, times(2)).receiveMessage(requestArgumentCaptor.capture());
        assertThat(requestArgumentCaptor.getAllValues().get(0).maxNumberOfMessages()).isEqualTo(5);
        assertThat(requestArgumentCaptor.getAllValues().get(1).maxNumberOfMessages()).isEqualTo(1);
    }

    @Test
    public void willAttemptToGetAllPrefetchMessagesOnFirstCall() throws InterruptedException {
        // arrange
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final CountDownLatch messagesReceivedLatch = new CountDownLatch(1);
        final PrefetchingProperties prefetchingProperties = DEFAULT_PREFETCHING_PROPERTIES.toBuilder()
                .desiredMinPrefetchedMessages(4)
                .maxPrefetchedMessages(5)
                .build();
        final PrefetchingMessageRetriever prefetchingMessageRetriever = buildRetriever(testCompletedLatch, prefetchingProperties);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(invocation -> {
                    messagesReceivedLatch.countDown();
                    return mockFutureWithGet(ReceiveMessageResponse.builder()
                            .messages(
                                    Message.builder().build(), Message.builder().build(), Message.builder().build(),
                                    Message.builder().build(), Message.builder().build()
                            )
                            .build());
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
        verify(sqsAsyncClient, times(1)).receiveMessage(requestArgumentCaptor.capture());
        assertThat(requestArgumentCaptor.getAllValues().get(0).maxNumberOfMessages()).isEqualTo(5);
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
                .build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(invocation -> {
                    firstmessagesRequested.countDown();
                    return mockFutureWithGet(ReceiveMessageResponse.builder().messages(Message.builder().build(), Message.builder().build()).build());
                })
                .thenAnswer(invocation -> {
                    secondmessagesRequested.countDown();
                    return mockFutureWithGet(ReceiveMessageResponse.builder().messages(Message.builder().build()).build());
                })
                .thenAnswer((invocationOnMock) -> {
                    thirdmessagesRequested.countDown();
                    return mockFutureWithGet(ReceiveMessageResponse.builder().messages(Message.builder().build()).build());
                });
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                prefetchingProperties, executorService);
        prefetchingMessageRetriever.start();

        // act
        firstmessagesRequested.await(1, SECONDS);
        Thread.sleep(1000); // Make sure we haven't called the second message
        assertThat(secondmessagesRequested.getCount()).isEqualTo(1L);
        prefetchingMessageRetriever.retrieveMessage();
        secondmessagesRequested.await(1, SECONDS);
        prefetchingMessageRetriever.retrieveMessage();
        thirdmessagesRequested.await(1, SECONDS);

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> requestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient, times(3)).receiveMessage(requestArgumentCaptor.capture());
        assertThat(requestArgumentCaptor.getAllValues().get(0).maxNumberOfMessages()).isEqualTo(4);
        assertThat(requestArgumentCaptor.getAllValues().get(1).maxNumberOfMessages()).isEqualTo(3);
        assertThat(requestArgumentCaptor.getAllValues().get(2).maxNumberOfMessages()).isEqualTo(3);
    }

    @Test
    public void noExceptionThrownForStoppingWhenAlreadyStopped() {
        // arrange
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                DEFAULT_PREFETCHING_PROPERTIES, executorService);

        // act
        prefetchingMessageRetriever.stop();
    }

    @Test
    public void cannotStartWhenRetrieverHasAlreadyStarted() {
        // arrange
        final CountDownLatch messagesRequestedLatch = new CountDownLatch(1);
        final PrefetchingMessageRetriever prefetchingMessageRetriever = new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES,
                DEFAULT_PREFETCHING_PROPERTIES, executorService);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer((invocation -> {
            messagesRequestedLatch.await();
            return mockFutureWithGet(ReceiveMessageResponse.builder());
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
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer((invocation -> {
            messagesRequestedLatch.countDown();
            stopRequestedLatch.await();
            return mockFutureWithGet(ReceiveMessageResponse.builder());
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
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer((invocation -> {
            messagesReceivedLatch.countDown();
            return mockFutureWithGet(ReceiveMessageResponse.builder().messages(Message.builder().build(), Message.builder().build()));
        }));
        prefetchingMessageRetriever.start();

        // act
        messagesReceivedLatch.await(1, TimeUnit.SECONDS);
        prefetchingMessageRetriever.stop();

        // assert
        verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
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
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureWithGet(ReceiveMessageResponse.builder()
                        .messages(Message.builder().build(), Message.builder().build())
                        .build()))
                .thenAnswer(waitUntilTestCompleted(secondPrefetchRequested, testCompletedLatch));

        try {
            // act
            prefetchingMessageRetriever.start();

            // assert
            prefetchingMessageRetriever.retrieveMessage();
            verify(sqsAsyncClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
            prefetchingMessageRetriever.retrieveMessage();
            secondPrefetchRequested.await(1, SECONDS);
            verify(sqsAsyncClient, times(2)).receiveMessage(any(ReceiveMessageRequest.class));
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
        final Message expectedMessage = Message.builder().build();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(answerWithMockFutureThatThrows(new RuntimeException("error")))
                .thenAnswer(answerWithMockFutureWithGet(ReceiveMessageResponse.builder().messages(expectedMessage).build()))
                .thenAnswer(waitUntilTestCompleted(secondPrefetchRequested, testCompletedLatch));

        try {
            // act
            prefetchingMessageRetriever.start();
            final Message message = prefetchingMessageRetriever.retrieveMessage();

            // assert
            assertThat(message).isEqualTo(expectedMessage);
        } finally {
            prefetchingMessageRetriever.stop();
        }
    }

    private PrefetchingMessageRetriever buildRetriever(final CountDownLatch testCompletedLatch) {
        return buildRetriever(testCompletedLatch, DEFAULT_PREFETCHING_PROPERTIES);
    }

    private PrefetchingMessageRetriever buildRetriever(final CountDownLatch testCompletedLatch, final PrefetchingProperties prefetchingProperties) {
        return new PrefetchingMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, prefetchingProperties, executorService) {
            @Override
            public synchronized Future<Object> stop() {
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
    private <T> CompletableFuture<T> mockFutureWithGet(final T result) {
        final CompletableFuture<T> future = mock(CompletableFuture.class);
        try {
            when(future.get()).thenReturn(result);
        } catch (InterruptedException | ExecutionException exception) {
            // ignore
        }
        return future;
    }

    private Answer<CompletableFuture<ReceiveMessageResponse>> waitUntilTestCompleted(final CountDownLatch messageRequestedLatch, final CountDownLatch testCompletedLatch) {
        return (invocation) -> {
            messageRequestedLatch.countDown();
            testCompletedLatch.await(1, SECONDS);
            return mockFutureWithGet(ReceiveMessageResponse.builder().build());
        };
    }
}
