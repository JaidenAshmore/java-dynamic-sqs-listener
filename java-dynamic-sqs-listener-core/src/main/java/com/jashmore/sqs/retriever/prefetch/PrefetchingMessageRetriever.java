package com.jashmore.sqs.retriever.prefetch;

import com.google.common.base.Preconditions;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;

/**
 * Message retriever that allows for the pre-fetching of messages for faster throughput by making sure that there are always messages in a queue locally to be
 * pulled from when one is needed.
 *
 * <p>The way this works is via the usage of an internal {@link BlockingDeque} that is used to store the prefetched of messages. When the limit of messages to
 * prefetch has been hit the pushing of the message onto the queue will be blocked until a consumer is able to consume it. This means that the internal
 * implementation is a bit odd and could be hard to debug when something is going wrong.
 *
 * <p>For the explanation, a {@link PrefetchingMessageRetriever} is built with a min prefetch size of 8, maximum prefetch size of 15 and a max of 10 messages
 * able to be obtained in one call to SQS. The events that occur for this retriever are:
 *
 * <ol>
 *     <li>{@link PrefetchingMessageRetriever} constructor is called</li>
 *     <li>To accommodate this the internal queue is only made to be of size 7, which is one less than the min number of prefetched messages.  This is
 *         important.</li>
 *     <li>The {@link #start()} method is called which starts the prefetching of messages from SQS process.</li>
 *     <li>A request is made out to retrieve 10 messages from SQS. This is the limit provided by AWS so this is the maximum messages that can be requested
 *         in a single request</li>
 *     <li>SQS responds with 10 messages.</li>
 *     <li>7 messages are placed into {@link #internalMessageQueue} but no more able to be placed in due to the limit of the queue and therefore the
 *         {@link QueueMessageRetriever} thread is blocked until messages are consumed before placing the other 3 messages onto the queue.</li>
 *     <li>As the consumers begin to consume the messages from this retriever the queue begins to shrink in size until there are 7 messages in the queue but
 *         zero needing to be placed in from the original request.</li>
 *     <li>At this point the {@link QueueMessageRetriever} is free to go out and obtain more messages from SQS and it will attempt to retrieve
 *         8 messages (this is due to the limit of 15 messages at maximum being prefetched)</li>
 *     <li>This process repeats as more messages are consumed and placed onto the queues.</li>
 * </ol>
 *
 * <p>Note that because these messages are being prefetched they could be in the internal queue for a long period and could even remain in the prefetched queue
 * after the visibility timeout for the message has expired. This could cause it to be placed in the dead letter queue or attempted again at a future time.
 */
@Slf4j
public class PrefetchingMessageRetriever implements AsyncMessageRetriever {
    private final SqsAsyncClient sqsAsyncClient;
    private final QueueProperties queueProperties;
    private final ExecutorService executorService;
    private final PrefetchingProperties properties;
    private final BlockingQueue<Message> internalMessageQueue;

    private QueueMessageRetriever queueMessageRetriever;
    private Future<?> fetchingMessagesFuture;
    private CompletableFuture<Object> fetchingMessagesCompletedFuture;


    public PrefetchingMessageRetriever(final SqsAsyncClient sqsAsyncClient,
                                       final QueueProperties queueProperties,
                                       final PrefetchingProperties properties,
                                       final ExecutorService executorService) {
        Preconditions.checkNotNull(sqsAsyncClient, "sqsAsyncClient");
        Preconditions.checkNotNull(queueProperties, "queueProperties");
        Preconditions.checkNotNull(properties, "properties");
        Preconditions.checkNotNull(executorService, "executor");

        this.sqsAsyncClient = sqsAsyncClient;
        this.queueProperties = queueProperties;
        this.executorService = executorService;
        this.properties = properties;

        // As LinkedBlockingQueue does not allow for an empty queue we use a SynchronousQueue so that it will only get more messages until the consumer has
        // grabbed on from the retriever
        if (properties.getDesiredMinPrefetchedMessages() == 0) {
            this.internalMessageQueue = new SynchronousQueue<>();
        } else {
            this.internalMessageQueue = new LinkedBlockingQueue<>(properties.getDesiredMinPrefetchedMessages() - 1);
        }
    }

    @Override
    public synchronized void start() {
        if (fetchingMessagesFuture != null) {
            throw new IllegalStateException("PrefetchingMessageRetriever is already running");
        }

        log.info("Starting retrieval of messages");
        fetchingMessagesCompletedFuture = new CompletableFuture<>();
        queueMessageRetriever = new QueueMessageRetriever(fetchingMessagesCompletedFuture);
        fetchingMessagesFuture = executorService.submit(queueMessageRetriever);
    }

    @Override
    public synchronized Future<Object> stop() {
        if (fetchingMessagesFuture == null) {
            log.warn("This retriever isn't running so it cannot be stopped");
            return CompletableFuture.completedFuture("OK");
        }

        log.info("Stopping retrieval of new messages");
        fetchingMessagesFuture.cancel(true);

        try {
            return fetchingMessagesCompletedFuture;
        } finally {
            fetchingMessagesFuture = null;
            queueMessageRetriever = null;
            fetchingMessagesCompletedFuture = null;
        }
    }

    @Override
    public Message retrieveMessage() throws InterruptedException {
        return internalMessageQueue.take();
    }

    /**
     * This does the actually retrieval of the messages in the background where it places it into a {@link BlockingQueue} for retrieval.
     */
    @AllArgsConstructor
    private class QueueMessageRetriever implements Runnable {
        private final CompletableFuture<Object> completableFuture;

        @Override
        public void run() {
            boolean shouldStop = false;
            while (!shouldStop) {
                try {
                    final ReceiveMessageResponse result;
                    try {
                        result = retrieveMoreMessages();
                        log.debug("Retrieved {} new messages for {} existing messages. Total: {}",
                                result.messages().size(),
                                internalMessageQueue.size(),
                                internalMessageQueue.size() + result.messages().size()
                        );
                    } catch (final InterruptedException interruptedException) {
                        log.info("Thread interrupted. Exiting...");
                        shouldStop = true;
                        continue;
                    }

                    for (final Message message : result.messages()) {
                        try {
                            internalMessageQueue.put(message);
                        } catch (InterruptedException exception) {
                            log.warn("Thread interrupted. Exiting...");
                            shouldStop = true;
                        }
                    }
                } catch (final Throwable throwable) {
                    log.error("Exception thrown when retrieving messages", throwable);

                    try {
                        Thread.sleep(properties.getErrorBackoffTimeInMilliseconds());
                    } catch (final InterruptedException interruptedException) {
                        log.warn("Thread interrupted during error backoff. Exiting...");
                        shouldStop = true;
                    }
                }
            }
            log.info("Finished obtaining messages");
            completableFuture.complete("DONE");
        }

        private ReceiveMessageResponse retrieveMoreMessages() throws InterruptedException {
            final int numberOfPrefetchSlotsLeft = properties.getMaxPrefetchedMessages() - internalMessageQueue.size();
            final int numberOfMessagesToObtain = Math.min(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS, numberOfPrefetchSlotsLeft);

            log.debug("Retrieving {} messages asynchronously", numberOfMessagesToObtain);
            final ReceiveMessageRequest request = ReceiveMessageRequest
                    .builder()
                    .queueUrl(queueProperties.getQueueUrl())
                    .waitTimeSeconds(properties.getMaxWaitTimeInSecondsToObtainMessagesFromServer())
                    .visibilityTimeout(properties.getVisibilityTimeoutForMessagesInSeconds())
                    .maxNumberOfMessages(numberOfMessagesToObtain)
                    .build();
            try {
                final Future<ReceiveMessageResponse> receiveMessageResultFuture = sqsAsyncClient.receiveMessage(request);
                return receiveMessageResultFuture.get();
            } catch (final ExecutionException executionException) {
                throw new RuntimeException("Failed to obtain messages", executionException.getCause());
            }
        }
    }
}
