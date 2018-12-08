package com.jashmore.sqs.container;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.concurrent.GuardedBy;

/**
 * Simple container that will start and stop the retrieval of messages if it is an async implementation and also
 * start the broker to distribute these messages.
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleMessageListenerContainer implements MessageListenerContainer {
    /**
     * The identifier for this container.
     *
     * <p>This identifier must be unique across all other containers so that it can be uniquely obtained to start
     * or stop specifically.
     *
     * @see QueueContainerService#startContainer(String) for usage of this identifier
     * @see QueueContainerService#stopContainer(String) for usage of this identifier
     */
    private final String identifier;

    /**
     * The {@link MessageRetriever} that will be used in this container to obtain messages to process.
     *
     * <p>This is only included in the container because if it is an {@link AsyncMessageRetriever} the retrieval of
     * messages can be started.
     */
    private final MessageRetriever messageRetriever;

    /**
     * The {@link MessageBroker} that will be used in this container.
     *
     * <p>This will be what starts and stops the actual processing of messages as this is used to take the messages and send
     * them by the {@link MessageProcessor}.
     */
    private final MessageBroker messageBroker;

    /**
     * Stores whether the container is currently running.
     *
     * <p>This is kept thread safe by making sure all methods for this container are synchronized.
     */
    @GuardedBy("this")
    private volatile boolean isRunning;

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public synchronized void start() {
        if (isRunning) {
            return;
        }

        if (messageRetriever instanceof AsyncMessageRetriever) {
            ((AsyncMessageRetriever) messageRetriever).start();
        }

        messageBroker.start();

        isRunning = true;
    }

    @Override
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }

        try {
            final Future<?> messageBrokerStoppedFuture = messageBroker.stop();
            if (messageRetriever instanceof AsyncMessageRetriever) {
                ((AsyncMessageRetriever) messageRetriever).stop().get();
            }
            messageBrokerStoppedFuture.get();
        } catch (final InterruptedException | ExecutionException exception) {
            log.error("Error waiting for container to stop", exception.getCause());
        } finally {
            isRunning = false;
        }
    }
}
