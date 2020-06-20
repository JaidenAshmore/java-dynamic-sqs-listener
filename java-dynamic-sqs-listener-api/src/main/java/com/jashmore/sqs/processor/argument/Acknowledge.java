package com.jashmore.sqs.processor.argument;

import java.util.concurrent.CompletableFuture;

/**
 * Used to acknowledge the completion of a message being processed by the message consumer.
 *
 * <p>If the message consumer has a parameter with this type it will indicate to the {@link com.jashmore.sqs.processor.MessageProcessor} that it should
 * not automatically mark the message as a success on the completion of the method without an exception being thrown. Instead the message consumer must call
 * {@link Acknowledge#acknowledgeSuccessful()} to mark the message as completed.
 */
public interface Acknowledge {
    /**
     * Acknowledge that the message was successfully completed, which will result in it being removed from the queue.
     *
     * <p>Multiple calls to this has indeterminate behaviour and should not be done.
     *
     * @return the future that will be resolved if the message was succesfully resolved
     */
    CompletableFuture<?> acknowledgeSuccessful();
}
