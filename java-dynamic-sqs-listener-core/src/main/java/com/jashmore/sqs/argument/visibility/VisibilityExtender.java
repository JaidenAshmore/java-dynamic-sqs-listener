package com.jashmore.sqs.argument.visibility;

import java.util.concurrent.Future;

/**
 * Used to extend the visibility of a message that is being processed in the scenario that the processing of a message takes a long time.
 */
public interface VisibilityExtender {
    /**
     * The default amount of time that the message will be extended in visibility of {@link #extend()} is called.
     */
    int DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS = 120;

    /**
     * Extend the visibility of a message that is being processed by {@link #DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS} seconds so that the message will not be
     * retried even when it is happily processing the message.
     *
     * <p>This is useful if you are doing a task that can take a considerable amount of time to process. You can use this to extend the visibility
     * of the message until you have finished processing the message.
     *
     * @return a future for when the beat has been acknowledged
     */
    Future<?> extend();

    /**
     * Extend the visibility of a message that is being processed so that it will not go back for reprocessing by a given amount of time in seconds.
     *
     * <p>This is useful if you are doing a task that can take a considerable amount of time to process. You can use this to extend the visibility
     * of the message until you have finished processing the message.
     *
     * @return a future for when the beat has been acknowledged
     */
    Future<?> extend(int visibilityExtensionInSeconds);
}
