package com.jashmore.sqs.retriever.batching;

import static com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverConstants.DEFAULT_BACKOFF_TIME_IN_MS;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.RetrieverUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
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
public class BatchingMessageRetriever implements AsyncMessageRetriever {
    private final QueueProperties queueProperties;
    private final SqsAsyncClient sqsAsyncClient;
    private final BatchingMessageRetrieverProperties properties;

    private final AtomicInteger numberWaitingForMessages;
    private final BlockingQueue<Message> messagesDownloaded;
    private final Object shouldObtainMessagesLock;

    public BatchingMessageRetriever(final QueueProperties queueProperties,
                                    final SqsAsyncClient sqsAsyncClient,
                                    final BatchingMessageRetrieverProperties properties) {
        this.queueProperties = queueProperties;
        this.sqsAsyncClient = sqsAsyncClient;
        this.properties = properties;

        this.numberWaitingForMessages = new AtomicInteger();
        this.messagesDownloaded = new LinkedBlockingQueue<>();
        this.shouldObtainMessagesLock = new Object();
    }

    @VisibleForTesting
    BatchingMessageRetriever(final QueueProperties queueProperties,
                                    final SqsAsyncClient sqsAsyncClient,
                                    final BatchingMessageRetrieverProperties properties,
                                    final AtomicInteger numberWaitingForMessages,
                                    final BlockingQueue<Message> messagesDownloaded,
                                    final Object shouldObtainMessagesLock) {
        this.queueProperties = queueProperties;
        this.sqsAsyncClient = sqsAsyncClient;
        this.properties = properties;
        this.numberWaitingForMessages = numberWaitingForMessages;
        this.messagesDownloaded = messagesDownloaded;
        this.shouldObtainMessagesLock = shouldObtainMessagesLock;
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

    @Override
    public void run() {
        log.debug("Started background thread");
        while (true) {
            final int numberOfMessagesToObtain;
            synchronized (shouldObtainMessagesLock) {
                final int triggerValue = properties.getNumberOfThreadsWaitingTrigger();
                if ((numberWaitingForMessages.get() - messagesDownloaded.size()) < triggerValue) {
                    try {
                        waitForEnoughThreadsToRequestMessages(getPollingPeriodInMs(triggerValue));
                    } catch (InterruptedException exception) {
                        log.debug("Thread interrupted while waiting for messages");
                        break;
                    }
                }
                numberOfMessagesToObtain = Math.min(numberWaitingForMessages.get() - messagesDownloaded.size(),
                        AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
            }

            if (numberOfMessagesToObtain <= 0) {
                log.debug("Requesting 0 messages");
                // We don't want to go request out if there are no messages to retrieve
                continue;
            }

            log.debug("Requesting {} messages", numberOfMessagesToObtain);

            try {
                final ReceiveMessageResponse response;
                try {
                    response = sqsAsyncClient.receiveMessage(buildReceiveMessageRequest(numberOfMessagesToObtain))
                            .get();
                } catch (final InterruptedException interruptedException) {
                    log.debug("Thread interrupted while obtaining messages from SQS");
                    break;
                }
                try {
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
                    backoff(RetrieverUtils.safelyGetBackoffTime(properties.getErrorBackoffTimeInMilliseconds(), DEFAULT_BACKOFF_TIME_IN_MS));
                } catch (final InterruptedException interruptedException) {
                    log.trace("Thread interrupted during error backoff thread sleeping");
                    break;
                }
            }
        }
    }

    @SuppressFBWarnings("WA_NOT_IN_LOOP") // Suppressed because it is actually in a loop this is just for testing purposes
    @VisibleForTesting
    void waitForEnoughThreadsToRequestMessages(final long waitPeriodInMs) throws InterruptedException {
        shouldObtainMessagesLock.wait(waitPeriodInMs);
    }

    @VisibleForTesting
    void backoff(final long backoffTimeInMs) throws InterruptedException {
        Thread.sleep(backoffTimeInMs);
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
                .waitTimeSeconds(RetrieverUtils.safelyGetWaitTimeInSeconds(properties.getMessageWaitTimeInSeconds()));

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
     * Safely get the polling period in milliseconds, default to zero if no value is defined and logging a warning indicating that not setting a value
     * could cause this retriever to block forever if the number of threads never reaches
     * {@link BatchingMessageRetrieverProperties#getNumberOfThreadsWaitingTrigger()}.
     *
     * @param triggerValue the number of threads that would trigger the batch retrieval of messages
     * @return the polling period in ms
     */
    private long getPollingPeriodInMs(final int triggerValue) {
        return Optional.ofNullable(properties.getMessageRetrievalPollingPeriodInMs())
                .orElseGet(() -> {
                    log.warn("No polling period specifically set, defaulting to zero which will have the thread blocked until {} threads request messages",
                            triggerValue);
                    return 0L;
                });
    }
}
