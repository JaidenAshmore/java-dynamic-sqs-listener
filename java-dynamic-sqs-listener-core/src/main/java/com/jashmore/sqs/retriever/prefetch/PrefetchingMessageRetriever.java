package com.jashmore.sqs.retriever.prefetch;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverConstants.DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.util.properties.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkInterruptedException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
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
 *     <li>The {@link #run()}} method is called on a new thread which starts the prefetching of messages from SQS process.</li>
 *     <li>A request is made out to retrieve 10 messages from SQS. This is the limit provided by AWS so this is the maximum messages that can be requested
 *         in a single request</li>
 *     <li>SQS responds with 10 messages.</li>
 *     <li>7 messages are placed into {@link #internalMessageQueue} but no more able to be placed in due to the limit of the queue and therefore the
 *         background thread is blocked until messages are consumed before placing the other 3 messages onto the queue.</li>
 *     <li>As the consumers begin to consume the messages from this retriever the queue begins to shrink in size until there are 7 messages in the queue but
 *         zero needing to be placed in from the original request.</li>
 *     <li>At this point the background thread is free to go out and obtain more messages from SQS and it will attempt to retrieve
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
    private final PrefetchingMessageRetrieverProperties properties;

    private final BlockingQueue<Message> internalMessageQueue;
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

        // As LinkedBlockingQueue does not allow for an empty queue we use a SynchronousQueue so that it will only get more messages until the consumer has
        // grabbed on from the retriever
        if (properties.getDesiredMinPrefetchedMessages() == 1) {
            this.internalMessageQueue = new SynchronousQueue<>();
        } else {
            this.internalMessageQueue = new LinkedBlockingQueue<>(desiredMinPrefetchedMessages - 1);
        }
    }

    @VisibleForTesting
    PrefetchingMessageRetriever(final SqsAsyncClient sqsAsyncClient,
                                final QueueProperties queueProperties,
                                final PrefetchingMessageRetrieverProperties properties,
                                final BlockingQueue<Message> internalMessageQueue,
                                final int maxPrefetchedMessages) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.queueProperties = queueProperties;
        this.properties = properties;
        this.internalMessageQueue = internalMessageQueue;
        this.maxPrefetchedMessages = maxPrefetchedMessages;
    }

    @Override
    public Message retrieveMessage() throws InterruptedException {
        return internalMessageQueue.take();
    }

    /**
     * This will need to be run on a background thread to the actually retrieval of the messages and placing them on the {@link BlockingQueue} for retrieval.
     */
    @Override
    public void run() {
        log.info("Started PrefetchingMessageRetriever background thread");
        while (true) {
            try {
                try {
                    final ReceiveMessageResponse result = sqsAsyncClient.receiveMessage(buildReceiveMessageRequest())
                            .get();
                    log.trace("Retrieved {} new messages for {} existing messages. Total: {}",
                            result.messages().size(),
                            internalMessageQueue.size(),
                            internalMessageQueue.size() + result.messages().size()
                    );

                    for (final Message message : result.messages()) {
                        // This is what actually controls the desired prefetched messages. As this call is blocking until there is room in the queue
                        // it will keep adding messages into the queue and once it is complete it will go and download more messages via the while loop
                        internalMessageQueue.put(message);
                    }
                } catch (final InterruptedException exception) {
                    log.debug("Thread interrupted while placing messages onto queue. Exiting...");
                    break;
                }
            } catch (final ExecutionException | RuntimeException exception) {
                // Supposedly the SqsAsyncClient can get interrupted and this will remove the interrupted status from the thread and then wrap it
                // in it's own version of the interrupted exception...If this happens when the retriever is being shut down it will keep on processing
                // because it does not realise it is being shut down, therefore we have to check for this and quit if necessary
                if (exception instanceof ExecutionException) {
                    final Throwable executionExceptionCause = exception.getCause();
                    if (executionExceptionCause instanceof SdkClientException) {
                        if (executionExceptionCause.getCause() instanceof SdkInterruptedException) {
                            log.debug("Thread interrupted receiving messages");
                            break;
                        }
                    }
                }

                try {
                    log.error("Exception thrown when retrieving messages", exception);
                    Thread.sleep(getBackoffTimeInMs());
                } catch (final InterruptedException interruptedException) {
                    log.debug("Thread interrupted during error backoff. Exiting...");
                    break;
                }
            }
        }
        log.info("PrefetchingMessageRetriever background thread has been successfully stopped");
    }

    /**
     * Build the request that will download the messages from SQS.
     *
     * @return the request that will be sent to SQS
     */
    private ReceiveMessageRequest buildReceiveMessageRequest() {
        final int numberOfPrefetchSlotsLeft = maxPrefetchedMessages - internalMessageQueue.size();
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

    /**
     * Get the amount of time in milliseconds that the thread should wait after a failure to get messages.
     *
     * <p>Visible for testing as testing with the actual default backoff time makes the unit tests slow
     *
     * @return the amount of time to backoff on errors in milliseconds
     */
    @SuppressWarnings("Duplicates")
    @VisibleForTesting()
    int getBackoffTimeInMs() {
        return PropertyUtils.safelyGetPositiveOrZeroIntegerValue(
                "errorBackoffTimeInMilliseconds",
                properties::getErrorBackoffTimeInMilliseconds,
                DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS
        );
    }
}
