package com.jashmore.sqs.retriever;

import java.util.concurrent.Future;

/**
 * Message retriever that obtains messages in the background for consumption.
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
     *     <li>Subsequent calls to this method should do nothing as the background thread has already started</li>
     *     <li>Calls to this method after a {@link #stop()} has been initiated should not be blocked by the previous thread being stopped</li>
     * </ul>
     */
    void start();

    /**
     * Stop the retrieval of messages.
     *
     * <p>Requirements for this method include:
     *
     * <ul>
     *     <li>This method must be non-blocking and return once the background thread has been triggered to be stopped</li>
     *     <li>Calls to this method before {@link #start()} has been called should do nothing and return a resolved future</li>
     *     <li>Subsequent calls to this method should do nothing as the background thread has already stopped. It should return a resolved future</li>
     * </ul>
     *
     * @return future that will resolve when the background message retriever thread has stopped.
     */
    Future<?> stop();
}
