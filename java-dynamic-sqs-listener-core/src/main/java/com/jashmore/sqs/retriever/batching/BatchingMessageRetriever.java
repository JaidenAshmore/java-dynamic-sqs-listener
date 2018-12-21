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

        /**
         * UW_UNCOND_WAIT has been specifically ignored for this method as we are happy to wait another polling period to obtain messages. If it is deemed
         * that this is not appropriate (the polling period is very large), we can take another look at this.
         *
         * <p>The reason that it isn't easy to put the check for the {@link #numberWaitingForMessages} is just because they have the message doesn't mean
         * they have gone into the finally block to decrease this number so even though all consumers are fine it seems like we can run the retrieval code
         * again.
         */
        @Override
        public void run() {
            log.debug("Started background thread");
            while (true) {
                final int numberOfMessagesToObtain;
                synchronized (shouldObtainMessagesLock) {
                    if ((numberWaitingForMessages.get() - messagesDownloaded.size()) < properties.getNumberOfThreadsWaitingTrigger()) {
                        try {
                            shouldObtainMessagesLock.wait(properties.getMessageRetrievalPollingPeriodInMs());
                        } catch (InterruptedException exception) {
                            log.debug("Thread interrupted while waiting for messages");
                            break;
                        }
                    }
                    numberOfMessagesToObtain = Math.min(numberWaitingForMessages.get() - messagesDownloaded.size(),
                            AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
                    log.info("Requesting {} messages", numberOfMessagesToObtain);
                }

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
                    try {
                        for (final Message message : receiveMessageResult.getMessages()) {
                            messagesDownloaded.put(message);
                        }
                    } catch (InterruptedException interruptedException) {
                        log.debug("Thread interrupted while placing messages on internal queue");
                        break;
                    }
                } catch (final Throwable throwable) {
                    log.error("Error thrown trying to obtain messages", throwable);
                }
            }
            completedFuture.complete("Stopped");
        }
    }
}
