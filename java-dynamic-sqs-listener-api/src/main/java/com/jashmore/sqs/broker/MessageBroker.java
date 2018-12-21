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
     * Start the brokerage of messages from the queue to the processor in a background thread.
     *
     * <p>Requirements for this method include:
     *
     * <ul>
     *     <li>This method must be non-blocking and return once the background thread has started.</li>
     *     <li>If this broker has already been started when this method is called again a {@link IllegalStateException} will be thrown until a subsequent
     *         {@link #stop()} or {@link #stopWithChildrenThreadsInterrupted()} has been called.</li>
     *     <li>If this broker is being stopped by calling {@link #stop()} or {@link #stopWithChildrenThreadsInterrupted()}, a call to this
     *         method should not be blocked by the previous thread and should start a new thread.</li>
     * </ul>
     *
     * @throws IllegalStateException if the broker has already been started
     */
    void start();

    /**
     * Stop the brokerage of messages from the queue to the processor, returning the future that will be resolved when that shutdown is complete.
     *
     * <p>Requirements for this method include:
     *
     * <ul>
     *     <li>The broker background thread and any threads created by this background thread must be completed before the {@link Future} returned
     *         is resolved.</li>
     *     <li>The children threads processing the messages will not be interrupted and therefore will fully process the message before the {@link Future}
     *         is resolved.</li>
     *     <li>If this broker has not been started or has already been stopped, any calls to this method will throw an {@link IllegalStateException}.</li>
     *     <li>The returned {@link Future} does not have any requirements for the value resolved and therefore should not be relied upon.</li>
     * </ul>
     *
     * @return a future that will resolve when the message broker background thread and all child threads have been stopped/completed
     * @throws IllegalStateException if the broker has not been started or has already stopped
     */
    Future<Object> stop();

    /**
     * Stop the brokerage of messages from the queue to the processor, returning the future that will be resolved when that shutdown is complete.
     *
     * <p>Requirements for this method include:
     *
     * <ul>
     *     <li>The broker background thread and any threads created by this background thread must be completed before the {@link Future} returned
     *         is resolved.</li>
     *     <li>The children threads processing the messages will be interrupted and therefore may not fully complete processing the messages before the
     *         {@link Future} is resolved.</li>
     *     <li>If this broker has not been started or has already been stopped, any calls to this method will throw an {@link IllegalStateException}.</li>
     *     <li>The returned {@link Future} does not have any requirements for the value resolved and therefore should not be relied upon.</li>
     * </ul>
     *
     * @return a future that will resolve when the message broker and any child threads have been stopped
     * @throws IllegalStateException if the broker has not been started or has already stopped
     */
    Future<Object> stopWithChildrenThreadsInterrupted();
}
