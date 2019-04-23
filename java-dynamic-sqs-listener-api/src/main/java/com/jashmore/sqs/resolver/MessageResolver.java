package com.jashmore.sqs.resolver;

import com.jashmore.sqs.processor.MessageProcessor;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;

/**
 * Used by the {@link MessageProcessor} to resolve the messages that have been successfully processed.
 *
 * <p>This logic has been split off to a separate class because it allows for different implementations of how these messages should be deleted from the
 * queue, for example by buffering these requests into a single call out to the SQS Queue.
 */
public interface MessageResolver {
    /**
     * Resolve the message by deleting it from the SQS queue and therefore won't be picked up again if it has a redrive policy.
     *
     * @param message the message to resolve
     * @return a {@link CompletableFuture} that will be completed when the message has been successfully purged
     */
    CompletableFuture<?> resolveMessage(Message message);
}
