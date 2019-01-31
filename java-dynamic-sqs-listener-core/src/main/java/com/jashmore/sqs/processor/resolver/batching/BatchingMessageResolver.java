package com.jashmore.sqs.processor.resolver.batching;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.processor.resolver.MessageResolver;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

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
    private final ExecutorService executorService;

    /**
     * Queue that buffers all of the messages that are to be resolved.
     */
    @GuardedBy("bufferingLock")
    private final BlockingQueue<Message> bufferedMessagesToResolve = new LinkedBlockingQueue<>();

    /**
     * The future that indicates whether there is currently a background thread resolving messages.
     *
     * <p>This will only go from null to holding an actual value if a message has been placed into the queue. Once all of the messages have been succesfully
     * processed, this will move back to a null value.
     */
    @GuardedBy("bufferingLock")
    @VisibleForTesting
    Future<?> backgroundDeletionFuture = null;
    /**
     * Lock used to handle the time and size limit for buffering as well as making sure that there are not two background threads wanting to delete
     * messages from the queue.
     */
    private final Object bufferingLock = new Object();

    public BatchingMessageResolver(final QueueProperties queueProperties,
                                   final SqsAsyncClient sqsAsyncClient,
                                   final BatchingMessageResolverProperties properties) {
        this.queueProperties = queueProperties;
        this.sqsAsyncClient = sqsAsyncClient;
        this.properties = properties;
        this.executorService = Executors.newCachedThreadPool();
    }

    public BatchingMessageResolver(final QueueProperties queueProperties,
                                   final SqsAsyncClient sqsAsyncClient,
                                   final BatchingMessageResolverProperties properties,
                                   final ExecutorService executorService) {
        this.queueProperties = queueProperties;
        this.sqsAsyncClient = sqsAsyncClient;
        this.properties = properties;
        this.executorService = executorService;
    }

    @Override
    public void resolveMessage(final Message message) {
        bufferedMessagesToResolve.add(message);

        synchronized (bufferingLock) {
            if (backgroundDeletionFuture == null) {
                backgroundDeletionFuture = executorService.submit(new BackgroundMessageResolverThread());
            }

            if (bufferedMessagesToResolve.size() >= properties.getBufferingSizeLimit()) {
                log.trace("Buffering message resolver hit buffer message limit, triggering deletion");
                bufferingLock.notifyAll();
            }
        }
    }

    /**
     * Background thread that will be started when there is at least a single message in the queue to be resolved.
     *
     * <p>This thread will wait a certain amount of time before sending off the messages to be resolved or until the buffering size limit is reached
     * and {@link #bufferingLock#notifyAll()} is called. When this is done it will batch
     */
    private class BackgroundMessageResolverThread implements Runnable {
        @Override
        public void run() {
            while (true) {
                synchronized (bufferingLock) {
                    if (bufferedMessagesToResolve.size() < properties.getBufferingSizeLimit()) {
                        try {
                            bufferingLock.wait(properties.getBufferingTimeInMs());
                        } catch (InterruptedException interruptedException) {
                            log.debug("Thread interrupted, sending messages now");
                            submitMessageDeletionBatch();
                            break;
                        }
                    }
                }

                submitMessageDeletionBatch();

                synchronized (bufferingLock) {
                    if (bufferedMessagesToResolve.isEmpty()) {
                        backgroundDeletionFuture = null;
                        break;
                    }
                }
            }

            log.debug("Exited background thread");
        }

        /**
         * Submit a batch of buffered messages to be deleted from the queue.
         */
        private void submitMessageDeletionBatch() {
            final List<Message> messagesToDelete = Lists.newArrayList();
            bufferedMessagesToResolve.drainTo(messagesToDelete, properties.getBufferingSizeLimit());

            if (!messagesToDelete.isEmpty()) {
                final DeleteMessageBatchRequest deleteRequest = DeleteMessageBatchRequest.builder()
                        .queueUrl(queueProperties.getQueueUrl())
                        .entries(messagesToDelete.stream()
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
                                return;
                            }

                            if (!response.failed().isEmpty()) {
                                final List<String> messageIds = response.failed().stream()
                                        .map(BatchResultErrorEntry::id)
                                        .collect(Collectors.toList());
                                log.error("Failed to delete the following messages: {}", messageIds);
                                return;
                            }

                            log.debug("Messages successfully deleted: {}", messagesToDelete.size());
                        });
            }
        }
    }
}
