package com.jashmore.sqs.container;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Container used to handle the entire lifecycle of a wrapped method in the spring context.
 *
 * <p>The responsibility of this container is being able to start and stop the processing of messages for the wrapped method. This
 * may will start any background threads needed for any component of the library.
 *
 * <p>These containers must be thread safe as there could be multiple threads starting and stopping these containers.
 */
@ThreadSafe
public interface MessageListenerContainer {
    /**
     * The unique identifier for this container which should not be the same as any other container that is being used.
     *
     * <p>This is useful for logging so that you know which containers are starting and stopping, as well as providing the ability to uniquely grab a
     * container by the wrapping frameworks, e.g. Spring.
     *
     * <p>Note that in the core implementation the uniqueness of this identifier is not really necessary but ones you attach the core framework to an
     * application like Spring, the uniqueness is necessary.
     *
     * @return the unique identifier for this container
     */
    String getIdentifier();

    /**
     * Start processing messages for a queue by starting any necessary dependencies internally.
     *
     * <p>Calls to this method should be blocking until the message container has started up and messages are now being received by
     * the corresponding wrapped methods.
     *
     * <p>For example, this could involve starting {@link MessageBroker}s or {@link AsyncMessageRetriever} if they are being used
     * internally.
     */
    void start();

    /**
     * Stop listening for messages on the queue by stopping any necessary dependencies internally.
     *
     * <p>Calls to this method should be blocking until the message container has stopped processing new messages from the queue.
     *
     * <p>Implementation of this method could involve starting {@link MessageBroker}s or {@link AsyncMessageRetriever} if they are being used
     * internally.
     */
    void stop();
}
