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
     * Start the ability to retrieve messages from the queue.
     */
    void start();

    /**
     * Stop the retrieval of messages.
     */
    Future<?> stop();
}
