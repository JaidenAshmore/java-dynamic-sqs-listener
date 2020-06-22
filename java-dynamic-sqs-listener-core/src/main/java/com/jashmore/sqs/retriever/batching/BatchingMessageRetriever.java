package com.jashmore.sqs.retriever.batching;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverConstants.DEFAULT_BACKOFF_TIME_IN_MS;
import static com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverConstants.DEFAULT_BATCHING_PERIOD_IN_MS;
import static com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverConstants.DEFAULT_BATCHING_TRIGGER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.primitives.Ints;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.properties.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkInterruptedException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * This implementation of the {@link MessageRetriever} will group requests for messages into batches to reduce the number of times that messages are requested
 * from the SQS queue.
 *
 * <p>The advantage of this retriever is that the overall number of times that the SQS queue is queried are reduced but the overall throughput is reduced
 * because threads are waiting for the batch to be let through to get messages.
 */
@Slf4j
public class BatchingMessageRetriever implements MessageRetriever {
    private final QueueProperties queueProperties;
    private final SqsAsyncClient sqsAsyncClient;
    private final BatchingMessageRetrieverProperties properties;

    private final LinkedBlockingDeque<CompletableFuture<Message>> futuresWaitingForMessages;

    public BatchingMessageRetriever(final QueueProperties queueProperties,
                                    final SqsAsyncClient sqsAsyncClient,
                                    final BatchingMessageRetrieverProperties properties) {
        this.queueProperties = queueProperties;
        this.sqsAsyncClient = sqsAsyncClient;
        this.properties = properties;

        this.futuresWaitingForMessages = new LinkedBlockingDeque<>();
    }

    @Override
    public CompletableFuture<Message> retrieveMessage() {
        final CompletableFuture<Message> messageCompletableFuture = new CompletableFuture<>();
        futuresWaitingForMessages.add(messageCompletableFuture);
        return messageCompletableFuture;
    }

    @Override
    public List<Message> run() {
        log.info("Started MessageRetriever");
        while (!Thread.currentThread().isInterrupted()) {
            final Queue<CompletableFuture<Message>> messagesToObtain;
            try {
                messagesToObtain = obtainRequestForMessagesBatch();
            } catch (final InterruptedException interruptedException) {
                log.debug("Thread interrupted waiting for batch");
                break;
            }

            log.debug("Requesting {} messages", messagesToObtain.size());

            if (messagesToObtain.isEmpty()) {
                continue;
            }

            final List<Message> messages;
            try {
                messages = CompletableFuture.supplyAsync(messagesToObtain::size)
                        .thenApply(this::buildReceiveMessageRequest)
                        .thenComposeAsync(sqsAsyncClient::receiveMessage)
                        .thenApply(ReceiveMessageResponse::messages)
                        .get();
            } catch (final RuntimeException | ExecutionException exception) {
                // Supposedly the SqsAsyncClient can get interrupted and this will remove the interrupted status from the thread and then wrap it
                // in it's own version of the interrupted exception...If this happens when the retriever is being shut down it will keep on processing
                // because it does not realise it is being shut down, therefore we have to check for this and quit if necessary
                if (exception instanceof ExecutionException) {
                    final Throwable executionExceptionCause = exception.getCause();
                    if (executionExceptionCause instanceof SdkClientException) {
                        if (executionExceptionCause.getCause() instanceof SdkInterruptedException) {
                            log.debug("Thread interrupted while receiving messages");
                            break;
                        }
                    }
                }
                log.error("Error request messages", exception);
                // If there was an exception receiving messages we need to put these back into the queue
                futuresWaitingForMessages.addAll(messagesToObtain);
                performBackoff();
                continue;
            } catch (final InterruptedException interruptedException) {
                log.debug("Thread interrupted while waiting for batch of messages");
                break;
            }

            log.debug("Downloaded {} messages", messages.size());
            if (messages.size() > messagesToObtain.size()) {
                log.error("More messages were downloaded than requested, this shouldn't happen");
            }

            for (final Message message : messages) {
                final CompletableFuture<Message> completableFuture = messagesToObtain.poll();
                if (completableFuture != null) {
                    completableFuture.complete(message);
                }
            }
            // Any threads that weren't completed send back for processing again
            futuresWaitingForMessages.addAll(messagesToObtain);
        }
        futuresWaitingForMessages.forEach(future -> future.cancel(true));
        log.info("MessageRetriever has been successfully stopped");
        return ImmutableList.of();
    }

    private Queue<CompletableFuture<Message>> obtainRequestForMessagesBatch() throws InterruptedException {
        final Queue<CompletableFuture<Message>> messagesToObtain = Lists.newLinkedList();
        final int batchSize = getbatchSize();
        final long pollingPeriod = getMaxBatchingPeriodInMs();
        if (log.isDebugEnabled()) {
            log.debug("Waiting for {} requests for messages {}. Total currently waiting: {}",
                    batchSize,
                    pollingPeriod == Long.MAX_VALUE ? "until batch size reached" : "within " + pollingPeriod + "ms",
                    futuresWaitingForMessages.size()
            );
        }
        Queues.drain(futuresWaitingForMessages, messagesToObtain, batchSize, pollingPeriod, TimeUnit.MILLISECONDS);
        return messagesToObtain;
    }

    private void performBackoff() {
        try {
            final long errorBackoffTimeInMilliseconds = getErrorBackoffTimeInMilliseconds();
            log.debug("Backing off for {}ms", errorBackoffTimeInMilliseconds);
            Thread.sleep(errorBackoffTimeInMilliseconds);
        } catch (final InterruptedException interruptedException) {
            log.debug("Thread interrupted during backoff period");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get the number of seconds that the thread should wait when there was an error trying to organise a thread to process.
     *
     * @return the backoff time in milliseconds
     * @see ConcurrentMessageBrokerProperties#getErrorBackoffTimeInMilliseconds() for more information
     */
    private long getErrorBackoffTimeInMilliseconds() {
        return PropertyUtils.safelyGetPositiveOrZeroLongValue(
                "errorBackoffTimeInMilliseconds",
                properties::getErrorBackoffTimeInMilliseconds,
                DEFAULT_BACKOFF_TIME_IN_MS
        );
    }

    /**
     * Safely get the total number of threads requiring messages before it sends a batch request for messages.
     *
     * @return the total number of threads for the batching trigger
     */
    private int getbatchSize() {
        final int batchSize = PropertyUtils.safelyGetIntegerValue(
                "batchSize",
                properties::getBatchSize,
                DEFAULT_BATCHING_TRIGGER
        );
        return Ints.constrainToRange(batchSize, 0, AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS);
    }

    /**
     * Build the request that will download the messages from SQS.
     *
     * @param numberOfMessagesToObtain the maximum number of messages to obtain
     * @return the request that will be sent to SQS
     */
    private ReceiveMessageRequest buildReceiveMessageRequest(final int numberOfMessagesToObtain) {
        final ReceiveMessageRequest.Builder requestBuilder = ReceiveMessageRequest.builder()
                .queueUrl(queueProperties.getQueueUrl())
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames(QueueAttributeName.ALL.toString())
                .maxNumberOfMessages(numberOfMessagesToObtain)
                .waitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);

        try {
            final Integer visibilityTimeoutInSeconds = properties.getMessageVisibilityTimeoutInSeconds();
            if (visibilityTimeoutInSeconds != null) {
                if (visibilityTimeoutInSeconds <= 0) {
                    log.warn("Non-positive visibilityTimeoutInSeconds provided: {}", visibilityTimeoutInSeconds);
                } else {
                    requestBuilder.visibilityTimeout(visibilityTimeoutInSeconds);
                }
            }
        } catch (final RuntimeException exception) {
            log.error("Error getting visibility timeout, none will be supplied in request", exception);
        }

        return requestBuilder.build();
    }

    /**
     * Safely get the polling period in milliseconds, default to zero if no value is defined and logging a warning indicating that not setting a value
     * could cause this retriever to block forever if the number of threads never reaches
     * {@link BatchingMessageRetrieverProperties#getBatchSize()}.
     *
     * @return the polling period in ms
     */
    private long getMaxBatchingPeriodInMs() {
        return PropertyUtils.safelyGetLongValue(
                "batchingPeriodInMs",
                properties::getBatchingPeriodInMs,
                DEFAULT_BATCHING_PERIOD_IN_MS
        );
    }
}
