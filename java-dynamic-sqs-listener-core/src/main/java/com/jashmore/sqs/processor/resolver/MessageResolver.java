package com.jashmore.sqs.processor.resolver;

import com.jashmore.sqs.processor.DefaultMessageProcessor;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Used by the {@link DefaultMessageProcessor} to resolve the messages that have been successfully processed.
 *
 * <p>This logic has been split off to a separate class because it allows for different implementations of how these messages should be deleted from the
 * queue, for example by buffering these requests into a single call out to the SQS Queue.
 */
public interface MessageResolver {
    /**
     * Resolve the message by deleting it from the SQS queue and therefore won't be picked up again if it has a redrive policy.
     *
     * @param message the message to resolve
     */
    void resolveMessage(Message message);
}
