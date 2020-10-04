package com.jashmore.sqs.decorator;

import com.jashmore.documentation.annotations.NotThreadSafe;
import java.time.Duration;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Properties for configuring the {@link AutoVisibilityExtenderMessageProcessingDecorator}.
 *
 * <p>These properties do not need to thread safe as the decorator will not be performing concurrent calls to these methods due to synchronization.
 */
@NotThreadSafe
public interface AutoVisibilityExtenderMessageProcessingDecoratorProperties {
    /**
     * Determine the length of time the visibility timeout for the message should be extended to.
     *
     * <p>If {@link #visibilityTimeout(Message)} is overridden, this should likely throw an {@link UnsupportedOperationException}.
     *
     * @return the visibility timeout for any message
     */
    Duration visibilityTimeout();

    /**
     * The maximum duration that a message should be allowed to process before it should be stopped.
     *
     * @return the time to keep processing the message and extending its visibility
     */
    Duration maxDuration();

    /**
     * The length of time before the visibility timeout expires to actually call to extend it.
     *
     * <p>As there can be delays in the visibility extender from starting or makings calls out to AWS, this should be set to a value to give you ample room
     * to extend the visibility before it expires. This value must be less the provided visibility timeout of the message.
     *
     * <p>It can be also be used to support a higher guarantee of successfully extending the message if there is a blip in the success rate of extending the
     * messages. As the {@link AutoVisibilityExtenderMessageProcessingDecorator} does not attempt to fix failing extensions you can use this value
     * to make sure multiple attempts are made to extend the message before it will actually have the visibility expire. or example, you could have
     * the {@link #visibilityTimeout()} to be 30 seconds but this value to be 20 seconds and therefore you will have 3 attempts to successfully extend
     * the message before it expires.
     *
     * @return the buffer time before the visibility timeout expires to try and extend the visibility
     */
    default Duration bufferDuration() {
        return Duration.ofSeconds(2);
    }

    /**
     * Determine the length of time the visibility timeout for the message should be extended to.
     *
     * <p>This provides the {@link Message} that the visibility timeout is for so implementations of this can do smarter logic, like exponentially increasing
     * the visibility timeout.
     *
     * @param message the message for
     * @return the visibility timeout for this message
     */
    default Duration visibilityTimeout(final Message message) {
        return visibilityTimeout();
    }

    /**
     * Callback for when a message has completed processing.
     *
     * <p>This can be used to clean up any state for this message, such as maintaining information about the visibility timeout for this message should be
     * next.
     *
     * <p>Note that this method may be called multiple times and therefore should be idempotent.
     *
     * @param message the message that has finished processing.
     */
    default void messageDoneProcessing(final Message message) {
        // override if desired
    }
}
