package com.jashmore.sqs.argument.acknowledge;

import java.util.concurrent.Future;

/**
 * Used to acknowledge the completion of a message being processed by the message consumer.
 *
 * <p>If the message consumer has a parameter with this type it will indicate to the message listener container to not
 * mark the message as a success on the completion of the method. Instead the message consumer must call
 * {@link Acknowledge#acknowledgeSuccessful()} to mark the message as completed.
 */
public interface Acknowledge {
    /**
     * Acknowledge that the message was successfully completed.
     *
     * <p>Multiple calls to this has indeterminate behaviour and should not be done.
     *
     * @return a future that is resolved when the message has been marked as acknowledged
     */
    Future<?> acknowledgeSuccessful();
}
