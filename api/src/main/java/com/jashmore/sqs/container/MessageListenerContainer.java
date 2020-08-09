package com.jashmore.sqs.container;

import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Container used to handle the entire lifecycle of a message listener for a queue.
 *
 * <p>This container should bundle all of the components of the library and allow for the starting and stopping of the container, e.g. starting and stopping
 * listening to messages.
 *
 * <p>These containers must be thread safe as there could be multiple threads requesting for the containers to start and stop, e.g. think of a web API that is
 * used to toggle the state of the container.
 */
@ThreadSafe
public interface MessageListenerContainer {
    /**
     * The unique identifier for this container.
     *
     * <p>This will be used to identify the threads for the background threads that are running, logging so that you know which containers are starting
     * and stopping, as well as providing the ability to uniquely grab a container by the wrapping frameworks, e.g. Spring.  Therefore this identifier
     * must be unique across the system.
     *
     * @return the unique identifier for this container
     */
    String getIdentifier();

    /**
     * Start this container so that it will begin to processing messages for a queue.
     *
     * <p>Calls to this method should start the main container process on a background thread and be blocking until this has started.  This should return
     * a {@link CompletableFuture} that is resolved when the container is eventually stopped.
     *
     * <p>Requirements for this method are:
     * <ul>
     *     <li>(required) all components should be started in a background thread when applicable, e.g. {@link MessageRetriever} and
     *     {@link MessageResolver}</li>
     *     <li>(required) calling this method when it has already started should do no operation. Having the container running multiple times would
     *     significantly complicate the implementations.</li>
     *     <li>(recommended) every time the container is started a new set of the components should be used.  This is because the API for these components
     *     does not require that the component to be able to be restarted gracefully and therefore may not act as expected.</li>
     * </ul>
     *
     * @return the future for when the container is eventually stopped
     */
    CompletableFuture<?> start();

    /**
     * Stop the container from listening for messages on the queue.
     *
     * <p>Calls to this method should be blocking until the container has triggered the shutdown of all of the background threads that were spun up as part
     * of the {@link #start()} call.  If this is called when it is not currently running, no operation should be done. Once the container has been stopped
     * another call to {@link #start()} may be done to start it up again.
     *
     * <p>Requirements for this method are:
     * <ul>
     *     <li>any extra messages that were downloaded from the {@link MessageRetriever} should be allowed to process if desired. This handles
     *     the case that there is no re-drive policy and losing a message is not desirable.</li>
     * </ul>
     */
    void stop();

    /**
     * Stop the container from listening for messages on the queue.
     *
     * <p>Calls to this method should be blocking until the container has triggered the shutdown of all of the background threads that were spun up as part
     * of the {@link #start()} call.  If this is called when it is not currently running, no operation should be done. Once the container has been stopped
     * another call to {@link #start()} may be done to start it up again.
     *
     * <p>Requirements for this method are:
     * <ul>
     *     <li>any extra messages that were downloaded from the {@link MessageRetriever} should be allowed to process if desired. This handles
     *     the case that there is no re-drive policy and losing a message is not desirable.</li>
     * </ul>
     *
     * @param duration the amount of time to wait for the container to stop
     */
    void stop(Duration duration);
}
