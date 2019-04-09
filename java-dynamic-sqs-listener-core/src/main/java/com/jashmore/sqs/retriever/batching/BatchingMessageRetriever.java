package com.jashmore.sqs.retriever.batching;

import static com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverConstants.DEFAULT_BACKOFF_TIME_IN_MS;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This implementation of the {@link MessageRetriever} will group requests for messages into batches to reduce the number of times that messages are requested
 * from the SQS queue.
 *
 * <p>The advantage of this retriever is that the overall number of times that the SQS queue is queried are reduced but the overall throughput is reduced
 * because threads are waiting for the batch to be let through to get messages.
 */
@Slf4j
@RequiredArgsConstructor
public class BatchingMessageRetriever implements AsyncMessageRetriever {
    private final QueueProperties queueProperties;
    private final SqsAsyncClient sqsAsyncClient;
    private final ExecutorService executorService;
    private final BatchingMessageRetrieverProperties properties;

    private final AtomicInteger numberWaitingForMessages = new AtomicInteger();
    private final BlockingQueue<Message> messagesDownloaded = new LinkedBlockingQueue<>();
    private final Object shouldObtainMessagesLock = new Object();

    private Future<?> backgroundThreadFuture;
    private CompletableFuture<Object> backgroundThreadStoppingFuture;

    @Override
    public synchronized void start() {
        if (backgroundThreadStoppingFuture != null) {
            log.warn("Retriever has already started");
            return;
        }
        backgroundThreadStoppingFuture = new CompletableFuture<>();
        backgroundThreadFuture = executorService.submit(new BackgroundBatchingMessageRetriever(backgroundThreadStoppingFuture));
    }

    @Override
    public synchronized Future<Object> stop() {
        if (backgroundThreadFuture == null) {
            return CompletableFuture.completedFuture("Not running");
        }

        backgroundThreadFuture.cancel(true);
        final Future<Object> futureToReturn = backgroundThreadStoppingFuture;
        backgroundThreadFuture = null;
        backgroundThreadStoppingFuture = null;
        return futureToReturn;
    }

    @Override
    public Message retrieveMessage() throws InterruptedException {
        try {
            incrementWaitingCountAndNotify();

            log.trace("Waiting for message");
            return messagesDownloaded.take();
        } finally {
            numberWaitingForMessages.decrementAndGet();
        }
    }

    /**
     * This increments the total count of threads waiting for messages and if it has hit the limit it will trigger the background thread to go get a message
     * now instead of waiting for the timeout.
     */
    private void incrementWaitingCountAndNotify() {
        synchronized (shouldObtainMessagesLock) {
            final int currentThreads = numberWaitingForMessages.incrementAndGet();
            if (currentThreads >= properties.getNumberOfThreadsWaitingTrigger()) {
                log.debug("Maximum number of threads({}) waiting has arrived requesting any sleeping threads to wake up to process",
                        properties.getNumberOfThreadsWaitingTrigger());
                // notify that we should grab a message
                shouldObtainMessagesLock.notifyAll();
            }
        }
    }

    /**
     * This is the background thread that will be obtaining the messages on a given cycle of
     * {@link BatchingMessageRetrieverProperties#getMessageRetrievalPollingPeriodInMs()} or until
     * {@link BatchingMessageRetrieverProperties#getNumberOfThreadsWaitingTrigger()} is reached, whichever is first. It will attempt to get those number of
     * messages that are waiting for retrieval in one call to SQS.
     */
    @AllArgsConstructor
    @VisibleForTesting
    class BackgroundBatchingMessageRetriever implements Runnable {
        private final CompletableFuture<Object> completedFuture;

        @Override
        public void run() {
            log.debug("Started background thread");
            while (true) {
                final int numberOfMessagesToObtain;
                synchronized (shouldObtainMessagesLock) {
                    final int triggerValue = properties.getNumberOfThreadsWaitingTrigger();
                    if ((numberWaitingForMessages.get() - messagesDownloaded.size()) < triggerValue) {
                        try {
                            shouldObtainMessagesLock.wait(getPollingPeriodInMs(triggerValue));
                        } catch (InterruptedException exception) {
                            log.debug("Thread interrupted while waiting for messages");
                            break;
                        }
                    }
                    numberOfMessagesToObtain = Math.min(numberWaitingForMessages.get() - messagesDownloaded.size(),
                            AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
                }

                log.debug("Requesting {} messages", numberOfMessagesToObtain);

                if (numberOfMessagesToObtain <= 0) {
                    // We don't want to go request out if there are no messages to retrieve
                    continue;
                }

                try {
                    try {
                        final ReceiveMessageResponse response = sqsAsyncClient.receiveMessage(buildReceiveMessageRequest(numberOfMessagesToObtain))
                                .get();
                        for (final Message message : response.messages()) {
                            messagesDownloaded.put(message);
                        }
                    } catch (InterruptedException interruptedException) {
                        log.debug("Thread interrupted while placing messages on internal queue");
                        break;
                    }
                } catch (final Throwable throwable) {
                    log.error("Error thrown trying to obtain messages", throwable);
                    try {
                        Thread.sleep(getBackoffTimeInMs());
                    } catch (final InterruptedException interruptedException) {
                        log.trace("Thread interrupted during error backoff thread sleeping");
                        break;
                    }
                }
            }
            completedFuture.complete("Stopped");
        }
    }

    /**
     * Build the request that will download the messages from SQS.
     *
     * @param numberOfMessagesToObtain the maximum number of messages to obtain
     * @return the request that will be sent to SQS
     */
    private ReceiveMessageRequest buildReceiveMessageRequest(final int numberOfMessagesToObtain) {
        final ReceiveMessageRequest.Builder requestBuilder = ReceiveMessageRequest
                .builder()
                .queueUrl(queueProperties.getQueueUrl())
                .maxNumberOfMessages(numberOfMessagesToObtain)
                .waitTimeSeconds(getWaitTimeInSeconds());

        final Integer visibilityTimeoutInSeconds = properties.getVisibilityTimeoutInSeconds();
        if (visibilityTimeoutInSeconds != null) {
            if (visibilityTimeoutInSeconds < 0) {
                log.warn("Non-positive visibilityTimeoutInSeconds provided: ", visibilityTimeoutInSeconds);
            } else {
                requestBuilder.visibilityTimeout(visibilityTimeoutInSeconds);
            }
        }

        return requestBuilder.build();
    }

    /**
     * Get the amount of time in milliseconds that the thread should wait after a failure to get messages.
     *
     * @return the amount of time to backoff on errors in milliseconds
     */
    @SuppressWarnings("Duplicates")
    private int getBackoffTimeInMs() {
        return Optional.ofNullable(properties.getErrorBackoffTimeInMilliseconds())
                .filter(backoffPeriod -> {
                    if (backoffPeriod > 0) {
                        return true;
                    } else {
                        log.warn("Non-positive errorBackoffTimeInMilliseconds provided({}), using default instead", backoffPeriod);
                        return false;
                    }
                })
                .orElse(DEFAULT_BACKOFF_TIME_IN_MS);
    }

    /**
     * Gets the wait time in seconds, defaulting to {@link AwsConstants#MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS} if it is not present.
     *
     * @return the amount of time to wait for messages from SQS
     */
    private int getWaitTimeInSeconds() {
        return Optional.ofNullable(properties.getMessageWaitTimeInSeconds())
                .orElse(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }

    /**
     * Safely get the polling period in milliseconds, default to zero if no value is defined and logging a warning indicating that not setting a value
     * could cause this retriever to block forever if the number of threads never reaches
     * {@link BatchingMessageRetrieverProperties#getNumberOfThreadsWaitingTrigger()}.
     *
     * @param triggerValue the number of threads that would trigger the batch retrieval of messages
     * @return the polling period in ms
     */
    private int getPollingPeriodInMs(final int triggerValue) {
        return Optional.ofNullable(properties.getMessageRetrievalPollingPeriodInMs())
                .orElseGet(() -> {
                    log.warn("No polling period specifically set, defaulting to zero which will have the thread blocked until {} threads request messages",
                            triggerValue);
                    return 0;
                });
    }
}
