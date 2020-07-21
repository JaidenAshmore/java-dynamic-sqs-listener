package com.jashmore.sqs.resolver.batching;

import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_IN_BATCH;

import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.util.collections.QueueUtils;
import com.jashmore.sqs.util.thread.ThreadUtils;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkInterruptedException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * {@link MessageResolver} that will batch the deletions of messages into a group to reduce the amount of messages that are being sent to SQS queue.
 *
 * <p>This uses a {@link BlockingQueue} to store all of the messages that need to be resolved and once the timeout provided by
 * {@link BatchingMessageResolverProperties#getBufferingTimeInMs()} is reached or the number of messages goes above
 * {@link BatchingMessageResolverProperties#getBufferingSizeLimit()}, the messages are sent out to be deleted.
 */
@Slf4j
@ThreadSafe
public class BatchingMessageResolver implements MessageResolver {
    private final QueueProperties queueProperties;
    private final SqsAsyncClient sqsAsyncClient;
    private final BatchingMessageResolverProperties properties;

    private final BlockingQueue<MessageResolutionBean> messagesToBeResolved;

    /**
     * Builds a {@link BatchingMessageResolver} that will perform a deletion of a message every time a single message is received.
     *
     * @param queueProperties details about the queue that the arguments will be resolved for
     * @param sqsAsyncClient  the client for connecting to the SQS queue
     */
    public BatchingMessageResolver(final QueueProperties queueProperties,
                                   final SqsAsyncClient sqsAsyncClient) {
        this(queueProperties, sqsAsyncClient, StaticBatchingMessageResolverProperties.builder()
                .bufferingSizeLimit(1)
                .bufferingTimeInMs(Long.MAX_VALUE)
                .build());
    }

    /**
     * Builds a {@link BatchingMessageResolver} with the provided properties.
     *
     * @param queueProperties details about the queue that the arguments will be resolved for
     * @param sqsAsyncClient  the client for connecting to the SQS queue
     * @param properties      configuration properties for this resolver
     */
    public BatchingMessageResolver(final QueueProperties queueProperties,
                                   final SqsAsyncClient sqsAsyncClient,
                                   final BatchingMessageResolverProperties properties) {
        this.queueProperties = queueProperties;
        this.sqsAsyncClient = sqsAsyncClient;
        this.properties = properties;

        this.messagesToBeResolved = new LinkedBlockingQueue<>();
    }

    @Override
    public CompletableFuture<?> resolveMessage(final Message message) {
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        messagesToBeResolved.add(new MessageResolutionBean(message, completableFuture));
        return completableFuture;
    }

    @Override
    public void run() {
        log.info("Started MessageResolver background thread");
        boolean continueProcessing = true;
        final ExecutorService executorService = buildExecutorServiceForSendingBatchDeletion();
        // all of the batches currently being sent so that they can be waited on during shutdown
        final List<CompletableFuture<?>> batchesBeingPublished = new ArrayList<>();
        while (!Thread.currentThread().isInterrupted() && continueProcessing) {
            final List<MessageResolutionBean> batchOfMessagesToResolve = new LinkedList<>();
            try {
                final int batchSize = getBatchSize();
                final long bufferingTimeInMs = getBufferingTimeInMs();
                log.trace("Waiting {}ms for {} messages to be submitted for deletion", bufferingTimeInMs, batchSize);
                QueueUtils.drain(messagesToBeResolved, batchOfMessagesToResolve, batchSize, Duration.ofMillis(bufferingTimeInMs));
            } catch (final InterruptedException interruptedException) {
                log.info("Shutting down MessageResolver");
                // Do nothing, we still want to send the current batch of messages
                continueProcessing = false;
            }

            if (!batchOfMessagesToResolve.isEmpty()) {
                log.debug("Sending batch deletion for {} messages", batchOfMessagesToResolve.size());
                final CompletableFuture<?> completableFuture = submitMessageDeletionBatch(batchOfMessagesToResolve, executorService);
                batchesBeingPublished.add(completableFuture);
                completableFuture
                        .whenComplete((response, throwable) -> batchesBeingPublished.remove(completableFuture));
            }
        }
        try {
            log.debug("Waiting for {} batches to complete", batchesBeingPublished.size());
            CompletableFuture.allOf(batchesBeingPublished.toArray(new CompletableFuture<?>[0]))
                    .get();
            executorService.shutdownNow();
            log.info("MessageResolver has been successfully stopped");
        } catch (final InterruptedException interruptedException) {
            log.warn("Thread interrupted while waiting for message batches to be completed");
            Thread.currentThread().interrupt();
        } catch (final ExecutionException executionException) {
            log.error("Error waiting for all message batches to be published", executionException.getCause());
        }
    }

    /**
     * Build the {@link ExecutorService} to send the batch message delete messages.
     *
     * <p>This is needed because when a thread is interrupted while using the {@link SqsAsyncClient} a {@link SdkInterruptedException} is thrown which is
     * ultimately not what we want. We instead want to know that this has been done and wait for the delete requests to eventually finish. Therefore,
     * running it on extra threads provides this extra safety.
     *
     * <p>The extra service also allows for multiple batches to be sent concurrently.
     *
     * @return the service for running message deletion on a separate thread
     */
    private ExecutorService buildExecutorServiceForSendingBatchDeletion() {
        return Executors.newCachedThreadPool(ThreadUtils.multiNamedThreadFactory(Thread.currentThread().getName() + "-batch-delete"));
    }

    /**
     * Safely get the batch size for the number of messages to resolve as AWS has a limit for how many messages can be sent at once.
     *
     * @return the number of messages that should be resolved in a single batch
     */
    private int getBatchSize() {
        final int bufferingSizeLimit = properties.getBufferingSizeLimit();
        if (bufferingSizeLimit < 1) {
            return 1;
        }
        return Math.min(bufferingSizeLimit, MAX_NUMBER_OF_MESSAGES_IN_BATCH);
    }

    /**
     * Get the time in milliseconds that the thread should wait for the batch to be filled.
     *
     * @return the time to wait in milliseconds
     */
    private long getBufferingTimeInMs() {
        return Math.max(0, properties.getBufferingTimeInMs());
    }

    /**
     * Submit the batch of messages to be resolved asynchronously.
     *
     * <p>When the batch is completed successfully (or unsuccessfully), the futures for each message will be completed.
     *
     * @param batchOfMessagesToResolve the messages to resolve
     */
    private CompletableFuture<?> submitMessageDeletionBatch(final List<MessageResolutionBean> batchOfMessagesToResolve,
                                                            final ExecutorService executorService) {
        final Map<String, CompletableFuture<Object>> messageCompletableFutures = batchOfMessagesToResolve.stream()
                .map(bean -> new AbstractMap.SimpleImmutableEntry<>(bean.getMessage().messageId(), bean.getCompletableFuture()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return CompletableFuture.supplyAsync(() -> buildBatchDeleteMessageRequest(batchOfMessagesToResolve))
                .thenComposeAsync(sqsAsyncClient::deleteMessageBatch, executorService)
                .whenComplete((response, exception) -> {
                    if (exception != null) {
                        log.error("Error deleting messages", exception);

                        messageCompletableFutures.values()
                                .forEach(completableFuture -> completableFuture.completeExceptionally(exception));
                        return;
                    }

                    log.debug("{} messages successfully deleted, {} failed", response.successful().size(), response.failed().size());

                    response.successful().stream()
                            .map(entry -> messageCompletableFutures.remove(entry.id()))
                            .forEach(completableFuture -> completableFuture.complete("completed"));

                    response.failed()
                            .forEach(entry -> {
                                final CompletableFuture<?> completableFuture = messageCompletableFutures.remove(entry.id());
                                completableFuture.completeExceptionally(new RuntimeException(entry.message()));
                            });

                    if (!messageCompletableFutures.isEmpty()) {
                        log.error("{} messages were not handled in the deletion. This could be a bug in the AWS SDK", messageCompletableFutures.size());
                        messageCompletableFutures.values()
                                .forEach(completableFuture -> completableFuture.completeExceptionally(
                                        new RuntimeException("Message not handled by batch delete. This should not happen")
                                ));
                    }
                });
    }

    private DeleteMessageBatchRequest buildBatchDeleteMessageRequest(final List<MessageResolutionBean> batchOfMessagesToResolve) {
        return DeleteMessageBatchRequest.builder()
                .queueUrl(queueProperties.getQueueUrl())
                .entries(batchOfMessagesToResolve.stream()
                        .map(MessageResolutionBean::getMessage)
                        .map(messageToDelete -> DeleteMessageBatchRequestEntry.builder()
                                .id(messageToDelete.messageId())
                                .receiptHandle(messageToDelete.receiptHandle())
                                .build())
                        .collect(Collectors.toSet())
                )
                .build();
    }

    /**
     * Internal bean used for storing the message to be resolved in the internal queue.
     */
    @Value
    @AllArgsConstructor
    private static class MessageResolutionBean {
        /**
         * The message to be resolved.
         */
        Message message;
        /**
         * The future that should be resolved when the message is successfully or unsuccessfully deleted.
         */
        CompletableFuture<Object> completableFuture;
    }
}
