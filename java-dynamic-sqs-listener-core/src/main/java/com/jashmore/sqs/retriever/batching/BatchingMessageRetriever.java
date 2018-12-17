package com.jashmore.sqs.retriever.batching;

import com.google.common.annotations.VisibleForTesting;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
    private final AmazonSQSAsync amazonSqsAsync;
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
    public synchronized Future<?> stop() {
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
        log.trace("Retrieving message");
        // Attempts to grab a message from the queue. This could happen if a call to retrieve a message timed out before it could take it from the queue
        final Message messageAlreadyInQueue = messagesDownloaded.poll();
        if (messageAlreadyInQueue != null) {
            return messageAlreadyInQueue;
        }

        try {
            incrementWaitingCountAndNotify();

            log.trace("Waiting for messager");
            return messagesDownloaded.take();
        } finally {
            numberWaitingForMessages.decrementAndGet();
        }
    }

    @Override
    public Optional<Message> retrieveMessage(@Min(0) final long timeout, @NotNull final TimeUnit timeUnit) throws InterruptedException {
        // Attempts to grab a message from the queue. This could happen if a call to retrieve a message timed out before it could take it from the queue
        final Message messageAlreadyInQueue = messagesDownloaded.poll();
        if (messageAlreadyInQueue != null) {
            return Optional.of(messageAlreadyInQueue);
        }

        try {
            log.info("Obtaining lock for incrementing number of messages");
            incrementWaitingCountAndNotify();
            log.info("Waiting for message to be obtained");

            return Optional.ofNullable(messagesDownloaded.poll(timeout, timeUnit));
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
                log.info("Maximum number of threads({}) waiting has arrived requesting any sleeping threads to wake up to process",
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
                synchronized (shouldObtainMessagesLock) {
                    try {
                        shouldObtainMessagesLock.wait(properties.getMessageRetrievalPollingPeriodInMs());
                    } catch (InterruptedException exception) {
                        log.debug("Thread interrupted while waiting for messages");
                        break;
                    }
                    final int numberOfMessagesToObtain = Math.min(numberWaitingForMessages.get(), AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
                    log.info("Requesting {} messages", numberOfMessagesToObtain);

                    if (numberOfMessagesToObtain == 0) {
                        // We don't want to go request out if there are no messages to retrieve
                        continue;
                    }

                    try {
                        final ReceiveMessageResult receiveMessageResult = amazonSqsAsync.receiveMessage(
                                new ReceiveMessageRequest(queueProperties.getQueueUrl())
                                        .withVisibilityTimeout(properties.getVisibilityTimeoutInSeconds())
                                        .withMaxNumberOfMessages(numberOfMessagesToObtain)
                                        .withWaitTimeSeconds(0)
                        );
                        messagesDownloaded.addAll(receiveMessageResult.getMessages());
                    } catch (final Throwable throwable) {
                        log.error("Error thrown trying to obtain messages", throwable);
                    }
                }
            }
            completedFuture.complete("Stopped");
        }
    }
}
