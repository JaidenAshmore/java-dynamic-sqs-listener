package com.jashmore.sqs.retriever.batching;

import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS;
import static com.jashmore.sqs.util.thread.ThreadTestUtils.startRunnableInThread;
import static com.jashmore.sqs.util.thread.ThreadTestUtils.waitUntilThreadInState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import com.jashmore.sqs.util.thread.ThreadTestUtils;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@Slf4j
@ExtendWith(MockitoExtension.class)
class BatchingMessageRetrieverTest {

    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder().queueUrl(QUEUE_URL).build();

    private static final Duration POLLING_PERIOD = Duration.ofSeconds(1);
    private static final StaticBatchingMessageRetrieverProperties DEFAULT_PROPERTIES = StaticBatchingMessageRetrieverProperties
        .builder()
        .messageVisibilityTimeout(Duration.ofSeconds(10))
        .batchSize(2)
        .batchingPeriod(POLLING_PERIOD)
        .build();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Test
    void threadIsWaitingWhileItWaitsForMessagesToDownload() {
        // arrange
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, DEFAULT_PROPERTIES);

        startRunnableInThread(
            retriever::run,
            thread -> {
                // act
                ThreadTestUtils.waitUntilThreadInState(thread, Thread.State.TIMED_WAITING);
                thread.interrupt();

                // assert
                ThreadTestUtils.waitUntilThreadInState(thread, Thread.State.TERMINATED);
            }
        );
    }

    @Test
    void whenThereAreMoreRequestsForMessagesThanTheThresholdItWillRequestMessagesStraightAway() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES.toBuilder().batchSize(2).build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        startRunnableInThread(
            retriever::run,
            thread -> {
                // act
                retriever.retrieveMessage();
                ThreadTestUtils.waitUntilThreadInState(thread, Thread.State.TIMED_WAITING);
                retriever.retrieveMessage();

                // assert
                assertThat(receiveMessageRequestLatch.await(1, TimeUnit.SECONDS)).isTrue();
            }
        );
    }

    @Test
    void whenThePollingPeriodIsHitTheBackgroundThreadWillRequestAsManyMessagesAsThoseWaiting() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES
            .toBuilder()
            .batchSize(2)
            .batchingPeriod(Duration.ofSeconds(1))
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        final long timeStarted = System.currentTimeMillis();
        startRunnableInThread(
            retriever::run,
            thread -> {
                // act
                retriever.retrieveMessage();
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();

                // assert
                final long timeSendingBatch = System.currentTimeMillis();
                assertThat(timeSendingBatch - timeStarted).isGreaterThanOrEqualTo(retrieverProperties.getBatchingPeriod().toMillis());
            }
        );
    }

    @Test
    void whenNoVisibilityTimeoutIncludedTheReceiveMessageRequestWillIncludeNullVisibilityTimeout() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES
            .toBuilder()
            .batchSize(1)
            .messageVisibilityTimeout(null)
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        // act
        startRunnableInThread(
            retriever::run,
            thread -> {
                retriever.retrieveMessage();
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    void whenNegativeVisibilityTimeoutIncludedTheReceiveMessageRequestWillIncludeNullVisibilityTimeout() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES
            .toBuilder()
            .batchSize(1)
            .messageVisibilityTimeout(Duration.ofSeconds(-1))
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        // act
        startRunnableInThread(
            retriever::run,
            thread -> {
                retriever.retrieveMessage();
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    void whenZeroVisibilityTimeoutIncludedTheReceiveMessageRequestWillIncludeNullVisibilityTimeout() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES
            .toBuilder()
            .batchSize(1)
            .messageVisibilityTimeout(Duration.ZERO)
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        // act
        startRunnableInThread(
            retriever::run,
            thread -> {
                retriever.retrieveMessage();
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    void whenValidVisibilityTimeoutIncludedTheReceiveMessageRequestWillIncludeVisibilityTimeout() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES
            .toBuilder()
            .batchSize(1)
            .messageVisibilityTimeout(Duration.ofSeconds(30))
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        // act
        startRunnableInThread(
            retriever::run,
            thread -> {
                retriever.retrieveMessage();
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isEqualTo(30);
    }

    @Test
    void requestsForBatchesOfMessagesWillNotBeExecutedConcurrently() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES
            .toBuilder()
            .batchSize(2)
            .batchingPeriod(Duration.ofMillis(50L))
            .messageVisibilityTimeout(Duration.ofSeconds(30))
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch firstRequestMade = new CountDownLatch(1);
        final CountDownLatch waitUntilSecondRequestSubmitted = new CountDownLatch(1);
        final CountDownLatch secondRequestMade = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                firstRequestMade.countDown();
                waitUntilSecondRequestSubmitted.await();
                return mockReceiveMessageResponse();
            })
            .thenAnswer(invocation -> {
                secondRequestMade.countDown();
                return mockReceiveMessageResponse(Message.builder().build(), Message.builder().build());
            });

        startRunnableInThread(
            retriever::run,
            thread -> {
                // act
                final CompletableFuture<Message> firstMessageResponse = retriever.retrieveMessage();
                assertThat(firstRequestMade.await(2, TimeUnit.SECONDS)).isTrue();
                final CompletableFuture<Message> secondMessageResponse = retriever.retrieveMessage();
                waitUntilSecondRequestSubmitted.countDown();
                assertThat(secondRequestMade.await(2, TimeUnit.SECONDS)).isTrue();

                // assert
                firstMessageResponse.get(1, TimeUnit.SECONDS);
                secondMessageResponse.get(1, TimeUnit.SECONDS);
                verify(sqsAsyncClient, times(2)).receiveMessage(any(ReceiveMessageRequest.class));
            }
        );
    }

    @Test
    void errorObtainingMessagesWillTryAgainAfterBackingOffPeriod() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES
            .toBuilder()
            .errorBackoffTime(Duration.ofMillis(200L))
            .batchSize(1)
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch secondMessageRequestedLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()))
            .thenAnswer(invocation -> {
                secondMessageRequestedLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        startRunnableInThread(
            retriever::run,
            thread -> {
                // act
                final long startTime = System.currentTimeMillis();
                retriever.retrieveMessage();
                assertThat(secondMessageRequestedLatch.await(1, TimeUnit.SECONDS)).isTrue();
                final long timeRequestedSecondMessageAfterBackoff = System.currentTimeMillis();

                // assert
                assertThat(timeRequestedSecondMessageAfterBackoff - startTime)
                    .isGreaterThanOrEqualTo(retrieverProperties.getErrorBackoffTime().toMillis());
            }
        );
    }

    @Test
    void interruptedExceptionThrownWhenBackingOffWillEndBackgroundThread() {
        // arrange
        final long errorBackoffTimeInMilliseconds = 200L;
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES
            .toBuilder()
            .errorBackoffTime(Duration.ofMillis(errorBackoffTimeInMilliseconds))
            .batchSize(1)
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));

        startRunnableInThread(
            retriever::run,
            thread -> {
                // act
                retriever.retrieveMessage();
                Thread.sleep(errorBackoffTimeInMilliseconds / 2);
                thread.interrupt();

                // assert
                waitUntilThreadInState(thread, Thread.State.TERMINATED);
            }
        );
    }

    @Test
    void willNotExceedAwsMaxMessagesForRetrievalWhenRequestingMessages() {
        // arrange
        final StaticBatchingMessageRetrieverProperties retrieverProperties = DEFAULT_PROPERTIES
            .toBuilder()
            .batchSize(1)
            .messageVisibilityTimeout(Duration.ofSeconds(30))
            .batchSize(MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1)
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        final CountDownLatch threadStoppedLatched = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                log.info("Requesting messages");
                receiveMessageRequestLatch.countDown();
                threadStoppedLatched.await();
                return mockReceiveMessageResponse();
            });

        // act
        startRunnableInThread(
            retriever::run,
            thread -> {
                for (int i = 0; i < MAX_NUMBER_OF_MESSAGES_FROM_SQS + 1; ++i) {
                    retriever.retrieveMessage();
                }
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().maxNumberOfMessages()).isEqualTo(MAX_NUMBER_OF_MESSAGES_FROM_SQS);
    }

    @Test
    void waitTimeForMessageRetrievalWillSqsMaximum() {
        // arrange
        final StaticBatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder().batchSize(1).build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, properties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        // act
        startRunnableInThread(
            retriever::run,
            thread -> {
                retriever.retrieveMessage();
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().waitTimeSeconds())
            .isEqualTo(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }

    @Test
    void allMessageAttributesShouldBeDownloadedWhenRequestingMessages() {
        // arrange
        final StaticBatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder().batchSize(1).build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, properties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        // act
        startRunnableInThread(
            retriever::run,
            thread -> {
                retriever.retrieveMessage();
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().messageSystemAttributeNames())
            .containsExactly(MessageSystemAttributeName.ALL);
    }

    @Test
    void allMessageSystemAttributesShouldBeDownloadedWhenRequestingMessages() {
        // arrange
        final StaticBatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder().batchSize(1).build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, properties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        // act
        startRunnableInThread(
            retriever::run,
            thread -> {
                retriever.retrieveMessage();
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().messageSystemAttributeNames())
            .containsExactly(MessageSystemAttributeName.ALL);
    }

    @Test
    void nullPollingPeriodWillStillAllowMessagesToBeReceivedWhenLimitReached() {
        // arrange
        final StaticBatchingMessageRetrieverProperties properties = DEFAULT_PROPERTIES
            .toBuilder()
            .batchSize(1)
            .batchingPeriod(null)
            .build();
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, properties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        startRunnableInThread(
            retriever::run,
            thread -> {
                // act
                retriever.retrieveMessage();

                // assert
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );
    }

    @Test
    void errorGettingVisibilityTimeoutWillNotProvideOneInRequest() {
        // arrange
        final BatchingMessageRetrieverProperties retrieverProperties = mock(BatchingMessageRetrieverProperties.class);
        when(retrieverProperties.getBatchSize()).thenReturn(1);
        when(retrieverProperties.getBatchingPeriod()).thenReturn(Duration.ofSeconds(1));
        when(retrieverProperties.getMessageVisibilityTimeout()).thenThrow(new ExpectedTestException());
        final BatchingMessageRetriever retriever = new BatchingMessageRetriever(QUEUE_PROPERTIES, sqsAsyncClient, retrieverProperties);
        final CountDownLatch receiveMessageRequestLatch = new CountDownLatch(1);
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(invocation -> {
                receiveMessageRequestLatch.countDown();
                return mockReceiveMessageResponse(Message.builder().build());
            });

        // act
        startRunnableInThread(
            retriever::run,
            thread -> {
                retriever.retrieveMessage();
                assertThat(receiveMessageRequestLatch.await(2, TimeUnit.SECONDS)).isTrue();
            }
        );

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(
            ReceiveMessageRequest.class
        );
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    private CompletableFuture<ReceiveMessageResponse> mockReceiveMessageResponse(final Message... messages) {
        return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(messages).build());
    }
}
