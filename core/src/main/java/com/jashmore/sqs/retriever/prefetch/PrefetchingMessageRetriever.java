package com.jashmore.sqs.retriever.prefetch;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverConstants.DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.Preconditions;
import com.jashmore.sqs.util.collections.CollectionUtils;
import com.jashmore.sqs.util.properties.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkInterruptedException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Message retriever that allows for the prefetching of messages for faster throughput by making sure that there are always messages in a queue locally to be
 * pulled from when one is needed.
 *
 * <p>The way this works is via the usage of the {@link PrefetchingMessageFutureConsumerQueue} that contains an internal queue of desired prefetched messages.
 * This retriever will keep trying to prefetch until this queue is filled or the {@link PrefetchingMessageRetriever#maxPrefetchedMessages} limit is reached.
 * If the number of prefetched messages is below the max but above the desired amount it will block until it is below the desired amount.
 *
 * <p>For the explanation, a {@link PrefetchingMessageRetriever} is built with a min prefetch size of 8, maximum prefetch size of 15 and a max of 10 messages
 * able to be obtained in one call to SQS. The events that occur for this retriever are:
 *
 * <ol>
 *     <li>{@link PrefetchingMessageRetriever} constructor is called</li>
 *     <li>To accommodate this the internal queue is only made to be of size 8.
 *     <li>The {@link #run()}} method is called on a new thread which starts the prefetching of messages from SQS process.</li>
 *     <li>A request is made out to retrieve 10 messages from SQS. This is the limit provided by AWS so this is the maximum messages that can be requested
 *         in a single request</li>
 *     <li>SQS responds with 10 messages.</li>
 *     <li>8 messages are placed into the queue but is blocked waiting for more messages to be placed due to the limit of the queue.  Therefore at this point
 *         the prefetching message retrieval is blocked until messages are consumed.  The other 2 messages will be waiting to be placed into this queue.
 *     <li>When two messages are consumed the rest of that prefetching batch is placed into the queue and the thread blocks until another message is
 *         consumed</li>
 *     <li>As the consumers begin to consume the messages from this retriever the queue begins to shrink in size until there are 7 messages in the queue.</li>
 *     <li>At this point the background thread is free to go out and obtain more messages from SQS and it will attempt to retrieve
 *         8 messages (this is due to the limit of 15 messages at maximum being prefetched)</li>
 *     <li>This process repeats as more messages are consumed and placed onto the queues.</li>
 * </ol>
 *
 * <p>Note that because these messages are being prefetched they could be in the internal queue for a long period and could even remain in the prefetched queue
 * after the visibility timeout for the message has expired. This could cause it to be placed in the dead letter queue or attempted again at a future time.
 */
@Slf4j
public class PrefetchingMessageRetriever implements MessageRetriever {
    private final SqsAsyncClient sqsAsyncClient;
    private final QueueProperties queueProperties;
    private final PrefetchingMessageRetrieverProperties properties;

    private final PrefetchingMessageFutureConsumerQueue pairConsumerQueue;
    private final int maxPrefetchedMessages;


    public PrefetchingMessageRetriever(final SqsAsyncClient sqsAsyncClient,
                                       final QueueProperties queueProperties,
                                       final PrefetchingMessageRetrieverProperties properties) {
        Preconditions.checkNotNull(sqsAsyncClient, "sqsAsyncClient");
        Preconditions.checkNotNull(queueProperties, "queueProperties");
        Preconditions.checkNotNull(properties, "properties");

        this.sqsAsyncClient = sqsAsyncClient;
        this.queueProperties = queueProperties;
        this.properties = properties;

        this.maxPrefetchedMessages = properties.getMaxPrefetchedMessages();
        final int desiredMinPrefetchedMessages = properties.getDesiredMinPrefetchedMessages();

        Preconditions.checkArgument(maxPrefetchedMessages >= desiredMinPrefetchedMessages,
                "maxPrefetchedMessages should be greater than or equal to desiredMinPrefetchedMessages");
        Preconditions.checkArgument(desiredMinPrefetchedMessages > 0, "desiredMinPrefetchedMessages must be greater than zero");

        pairConsumerQueue = new PrefetchingMessageFutureConsumerQueue(desiredMinPrefetchedMessages);
    }

    @Override
    public CompletableFuture<Message> retrieveMessage() {
        final CompletableFuture<Message> completableFuture = new CompletableFuture<>();
        pairConsumerQueue.pushCompletableFuture(completableFuture);
        return completableFuture;
    }

    @Override
    public List<Message> run() {
        log.info("Started MessageRetriever");

        final List<Message> listsNotPublished = new LinkedList<>();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                pairConsumerQueue.blockUntilFreeSlotForMessage();
                final List<Message> messages = CompletableFuture.supplyAsync(this::buildReceiveMessageRequest)
                        .thenCompose(sqsAsyncClient::receiveMessage)
                        .thenApply(ReceiveMessageResponse::messages)
                        .get();

                log.debug("Received {} messages", messages.size());

                final ListIterator<Message> messageListIterator = messages.listIterator();
                while (messageListIterator.hasNext()) {
                    final Message message = messageListIterator.next();
                    try {
                        pairConsumerQueue.pushMessage(message);
                    } catch (final InterruptedException interruptedException) {
                        log.debug("Thread interrupted while adding messages into internal queue. Exiting...");
                        listsNotPublished.add(message);
                        messageListIterator.forEachRemaining(listsNotPublished::add);
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (final InterruptedException exception) {
                log.debug("Thread interrupted while requesting messages. Exiting...");
                break;
            } catch (final ExecutionException | RuntimeException exception) {
                // Supposedly the SqsAsyncClient can get interrupted and this will remove the interrupted status from the thread and then wrap it
                // in it's own version of the interrupted exception...If this happens when the retriever is being shut down it will keep on processing
                // because it does not realise it is being shut down, therefore we have to check for this and quit if necessary
                if (exception instanceof ExecutionException) {
                    final Throwable executionExceptionCause = exception.getCause();
                    if (executionExceptionCause instanceof SdkClientException && executionExceptionCause.getCause() instanceof SdkInterruptedException) {
                        log.debug("Thread interrupted receiving messages");
                        break;
                    }
                }
                log.error("Exception thrown when retrieving messages", exception);
                performBackoff();
            }
        }

        final QueueDrain pairQueue = pairConsumerQueue.drain();
        pairQueue.getFuturesWaitingForMessages().forEach(future -> future.cancel(true));
        return CollectionUtils.immutableListFrom(pairQueue.getMessagesAvailableForProcessing(), listsNotPublished);
    }

    /**
     * Build the request that will download the messages from SQS.
     *
     * @return the request that will be sent to SQS
     */
    private ReceiveMessageRequest buildReceiveMessageRequest() {
        final int numberOfPrefetchSlotsLeft = maxPrefetchedMessages - pairConsumerQueue.getNumberOfBatchedMessages();
        final int numberOfMessagesToObtain = Math.min(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS, numberOfPrefetchSlotsLeft);

        log.debug("Retrieving {} messages asynchronously", numberOfMessagesToObtain);
        final ReceiveMessageRequest.Builder requestBuilder = ReceiveMessageRequest.builder()
                .queueUrl(queueProperties.getQueueUrl())
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames(QueueAttributeName.ALL.toString())
                .waitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .maxNumberOfMessages(numberOfMessagesToObtain);
        final Integer visibilityTimeoutInSeconds = properties.getMessageVisibilityTimeoutInSeconds();
        if (visibilityTimeoutInSeconds != null) {
            if (visibilityTimeoutInSeconds <= 0) {
                log.warn("Non-positive visibilityTimeoutInSeconds provided: {}", visibilityTimeoutInSeconds);
            } else {
                requestBuilder.visibilityTimeout(visibilityTimeoutInSeconds);
            }
        }

        return requestBuilder.build();
    }

    private void performBackoff() {
        try {
            final long errorBackoffTimeInMilliseconds = getBackoffTimeInMs();
            log.debug("Backing off for {}ms", errorBackoffTimeInMilliseconds);
            Thread.sleep(errorBackoffTimeInMilliseconds);
        } catch (final InterruptedException interruptedException) {
            log.debug("Thread interrupted during backoff period");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get the amount of time in milliseconds that the thread should wait after a failure to get messages.
     *
     * <p>Visible for testing as testing with the actual default backoff time makes the unit tests slow
     *
     * @return the amount of time to backoff on errors in milliseconds
     */
    @SuppressWarnings("Duplicates")
    private int getBackoffTimeInMs() {
        return PropertyUtils.safelyGetPositiveOrZeroIntegerValue(
                "errorBackoffTimeInMilliseconds",
                properties::getErrorBackoffTimeInMilliseconds,
                DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS
        );
    }
}
