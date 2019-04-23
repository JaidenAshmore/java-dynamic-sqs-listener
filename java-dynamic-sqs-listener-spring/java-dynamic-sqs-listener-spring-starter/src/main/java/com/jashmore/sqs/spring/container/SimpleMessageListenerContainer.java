package com.jashmore.sqs.spring.container;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.AsyncMessageResolver;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.spring.QueueContainerService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.concurrent.GuardedBy;

/**
 * Simple container that will start and stop the the retrieval of messages if it is an {@link AsyncMessageRetriever} as well as starting the
 * {@link MessageBroker} to distribute these messages.
 */
@Slf4j
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
     * The {@link MessageBroker} that will be used in this container.
     *
     * <p>This will be what starts and stops the actual processing of messages as this is used to take the messages and send
     * them by the {@link MessageProcessor}.
     */
    private final MessageResolver messageResolver;

    private final ExecutorService executorService;

    /**
     * Stores whether the container is currently running.
     *
     * <p>This is kept thread safe by making sure all methods for this container are synchronized.
     */
    @GuardedBy("this")
    private volatile boolean isRunning;

    @GuardedBy("this")
    private Future<?> messageResolverCompletableFuture;

    /**
     * Container that can be built when the {@link MessageBroker} is using an {@link AsyncMessageRetriever}. This takes the {@link AsyncMessageRetriever} so
     * that during the lifecycle of the spring container, it can be enabled and disabled at the same time that the {@link MessageBroker} is.
     *
     * @param identifier       the unique identifier for this container
     * @param messageRetriever the message retriever for this listener
     * @param messageBroker    the message broker that handles the processing of messages
     * @param messageResolver  the message resolver that will be used in this container
     */
    public SimpleMessageListenerContainer(final String identifier,
                                          final MessageRetriever messageRetriever,
                                          final MessageBroker messageBroker,
                                          final MessageResolver messageResolver) {
        this.identifier = identifier;
        this.messageRetriever = messageRetriever;
        this.messageBroker = messageBroker;
        this.messageResolver = messageResolver;
        this.executorService = Executors.newCachedThreadPool();

        this.messageResolverCompletableFuture = null;
    }

    @VisibleForTesting
    SimpleMessageListenerContainer(final String identifier,
                                   final MessageRetriever messageRetriever,
                                   final MessageBroker messageBroker,
                                   final MessageResolver messageResolver,
                                   final ExecutorService executorService) {
        this.identifier = identifier;
        this.messageRetriever = messageRetriever;
        this.messageBroker = messageBroker;
        this.messageResolver = messageResolver;
        this.executorService = executorService;

        this.messageResolverCompletableFuture = null;
    }

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
            ((AsyncMessageRetriever)messageRetriever).start();
        }

        if (messageResolver instanceof AsyncMessageResolver) {
            messageResolverCompletableFuture = executorService.submit((AsyncMessageResolver) messageResolver);
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
                ((AsyncMessageRetriever)messageRetriever).stop().get();
            }

            // TODO: All of the tests for these
            if (messageResolverCompletableFuture != null) {
                messageResolverCompletableFuture.cancel(true);
            }

            messageBrokerStoppedFuture.get();
        } catch (final InterruptedException | ExecutionException exception) {
            log.error("Error waiting for container to stop", exception.getCause());
        } finally {
            isRunning = false;
        }
    }
}
