package com.jashmore.sqs.retriever;

import java.util.concurrent.Future;

/**
 * {@link MessageRetriever} that obtains messages in the background for consumption.
 *
 * <p>For example, this could be used when messages are pulled from the queue and cached locally to improve performance by reducing the number
 * of times and latency of pulling messages from the queue.
 */
public interface AsyncMessageRetriever extends MessageRetriever {
    /**
     * Start retrieve messages from the queue in a background thread.
     *
     * <p>Requirements for this method include:
     *
     * <ul>
     *     <li>This method must be non-blocking and return once the background thread has started.</li>
     *     <li>If this retriever has already been started, any calls to this method will throw an {@link IllegalStateException}.</li>
     *     <li>If this retriever is being stopped by calling {@link #stop()}, a call to this method should <b>not</b> be blocked by the previous thread and
     *         should start a new thread.</li>
     * </ul>
     *
     * @throws IllegalStateException if the retriever has already been started
     */
    void start();

    /**
     * Stop the retrieval of messages.
     *
     * <p>Requirements for this method include:
     *
     * <ul>
     *     <li>This method must be non-blocking and return once the background thread has been triggered to stop.</li>
     *     <li>If this retriever has not been started or has already been stopped, any calls to this method will throw an {@link IllegalStateException}.</li>
     *     <li>The returned {@link Future} does not have any requirements for the value resolved and therefore should not be relied upon.</li>
     * </ul>
     *
     * @return future that will resolve when the background message retriever thread has stopped
     * @throws IllegalStateException if the retriever has not been started or has already stopped
     */
    Future<Object> stop();
}
