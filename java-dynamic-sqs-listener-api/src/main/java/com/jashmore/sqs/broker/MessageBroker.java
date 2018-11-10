package com.jashmore.sqs.broker;

import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;

import java.util.concurrent.Future;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Broker used to co-ordinate the retrieval and processing of messages from a remote queue.
 *
 * <p>If you were to consider this library as similar to a pub-sub system, this would be considered the broker that transports messages from the publisher
 * to the subscriber.  It will request for messages from the {@link MessageRetriever} and delegate the processing to a corresponding {@link MessageProcessor}.
 *
 * <p>As this is a non-blocking class that will start the message process in a background thread there is the possibility of usage of this class in multiple
 * threads. Therefore, implementations of this class must be thread safe.
 */
@ThreadSafe
public interface MessageBroker {
    /**
     * Start the brokerage of messages from the queue to the processor.
     *
     * <p>This must be non-blocking and start the process on a background thread.
     *
     * <p>If this broker has already been started then any subsequent calls to this method will do no action until {@link #stop(boolean)} has been
     * called. Once {@link #stop(boolean)} has been called, any calls to this method should spin up a new thread and it should not be dependent on the stop
     * finally finishing.
     */
    void start();

    /**
     * Request all threads that are currently listening for messages returning the future that will be resolved when that is completed.
     *
     * <p>This returns a future that will be resolved once the broker has successfully stopped. Note that once this method returns the internal state of the
     * message broker should be reset so that it can be started again
     *
     * <p>If this broker has not been started or has already been stopped, any calls to this method will do no action and a future that has already been
     * completed will be returned.
     *
     * @param interruptThreads whether the current threads processing the messages should be interrupted
     * @return a future that will resolve when the message broker and any child threads have been stopped
     */
    Future<?> stop(boolean interruptThreads);

    /**
     * Stop the message broker from processing any future messages returning the future that will be resolved when that is completed.
     *
     * <p>This will not interrupt any currently running threads.
     *
     * @return a future that will resolve when the message broker and any child threads have been stopped
     */
    default Future<?> stop() {
        return stop(false);
    }
}
