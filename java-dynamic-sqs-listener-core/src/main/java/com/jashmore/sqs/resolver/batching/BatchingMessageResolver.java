package com.jashmore.sqs.resolver.batching;

import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_IN_BATCH;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.primitives.Ints;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.resolver.AsyncMessageResolver;
import com.jashmore.sqs.resolver.MessageResolver;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.concurrent.ThreadSafe;

/**
 * {@link MessageResolver} that will batch the deletions of messages into a group to reduce the amount of messages that are being sent to SQS queue.
 *
 * <p>This uses a {@link BlockingQueue} to store all of the messages that need to be resolved and once the timeout provided by
 * {@link BatchingMessageResolverProperties#getBufferingTimeInMs()} is reached or the number of messages goes above
 * {@link BatchingMessageResolverProperties#getBufferingSizeLimit()}, the messages are sent out to be deleted.
 *
 * <p>As this is an {@link AsyncMessageResolver}, the convention for using this is to make sure that it is run on a background thread. For example:
 *
 * <pre class="code">
 *     final BatchingMessageResolver messageResolver = new BatchingMessageResolver(...);
 *     Future&lt;?&gt; resolverFuture = Executors.newCachedThreadPool().submit(messageResolver);
 *
 *     // other code...
 *
 *     CompletableFuture&lt;?&gt; resolveMessageFuture = messageResolver.resolverMessage(message);
 *
 *     // more code...
 *
 *     // Stop the message resolver when you are done
 *     resolverFuture.cancel(true);
 * </pre>
 */
@Slf4j
@ThreadSafe
public class BatchingMessageResolver implements AsyncMessageResolver {
    private final QueueProperties queueProperties;
    private final SqsAsyncClient sqsAsyncClient;
    private final BatchingMessageResolverProperties properties;

    private final BlockingQueue<MessageResolutionBean> messagesToBeResolved;

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
        messagesToBeResolved.add(MessageResolutionBean.builder()
                .message(message)
                .completableFuture(completableFuture)
                .build());
        return completableFuture;
    }

    @Override
    public void run() {
        boolean continueProcessing = true;
        while (continueProcessing) {
            try {
                final List<MessageResolutionBean> batchOfMessagesToResolve = new LinkedList<>();
                try {
                    Queues.drain(messagesToBeResolved, batchOfMessagesToResolve, getBatchSize(), getBufferingTimeInMs(), TimeUnit.MILLISECONDS);
                } catch (final InterruptedException interruptedException) {
                    // Do nothing, we still want to send the current batch of messages
                    continueProcessing = false;
                }

                if (!batchOfMessagesToResolve.isEmpty()) {
                    submitMessageDeletionBatch(batchOfMessagesToResolve);
                }
            } catch (final Throwable throwable) {
                log.error("Exception thrown when retrieving messages", throwable);
            }
        }
    }

    /**
     * Safely get the batch size for the number of messages to resolve as AWS has a limit for how many messages can be sent at once.
     *
     * @return the number of messages that should be resolved in a single batch
     */
    private int getBatchSize() {
        return Ints.constrainToRange(properties.getBufferingSizeLimit(), 1, MAX_NUMBER_OF_MESSAGES_IN_BATCH);
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
    private void submitMessageDeletionBatch(final List<MessageResolutionBean> batchOfMessagesToResolve) {
        final Map<String, CompletableFuture<Object>> messageCompletableFutures = batchOfMessagesToResolve.stream()
                .map(bean -> Maps.immutableEntry(bean.getMessage().messageId(), bean.getCompletableFuture()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        final DeleteMessageBatchRequest deleteRequest = DeleteMessageBatchRequest.builder()
                .queueUrl(queueProperties.getQueueUrl())
                .entries(batchOfMessagesToResolve.stream()
                        .map(MessageResolutionBean::getMessage)
                        .map(messageToDelete -> DeleteMessageBatchRequestEntry
                                .builder()
                                .id(messageToDelete.messageId())
                                .receiptHandle(messageToDelete.receiptHandle())
                                .build())
                        .collect(Collectors.toSet())
                )
                .build();

        sqsAsyncClient.deleteMessageBatch(deleteRequest)
                .whenComplete((response, exception) -> {
                    if (exception != null) {
                        log.error("Error deleting messages", exception);

                        messageCompletableFutures.values()
                                .forEach(completableFuture -> completableFuture.completeExceptionally(exception));
                        return;
                    }

                    response.successful().stream()
                            .map(entry -> messageCompletableFutures.remove(entry.id()))
                            .forEach(completableFuture -> completableFuture.complete("completed"));

                    response.failed()
                            .forEach(entry -> {
                                final CompletableFuture<?> completableFuture = messageCompletableFutures.remove(entry.id());
                                completableFuture.completeExceptionally(new RuntimeException(entry.message()));
                            });

                    messageCompletableFutures.values()
                            .forEach(completableFuture -> completableFuture.completeExceptionally(
                                    new RuntimeException("Message not handled by batch delete. This should not happen")
                            ));
                });
    }

    /**
     * Internal bean used for storing the message to be resolved in the internal queue.
     */
    @Value
    @Builder
    private static class MessageResolutionBean {
        /**
         * The message to be resolved.
         */
        private final Message message;
        /**
         * The future that should be resolved when the message is successfully or unsuccessfully deleted.
         */
        private final CompletableFuture<Object> completableFuture;
    }
}
