package com.jashmore.sqs.container;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.AsyncMessageResolver;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.GuardedBy;

/**
 * Simple container that will start and stop the the retrieval of messages if it is an {@link AsyncMessageRetriever} as well as starting the
 * {@link MessageBroker} to distribute these messages.
 */
@Slf4j
public class SimpleMessageListenerContainer implements MessageListenerContainer {

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

    @GuardedBy("this")
    private ExecutorService executorService;

    /**
     * Container that can be built when the {@link MessageBroker} is using an {@link AsyncMessageRetriever}. This takes the {@link AsyncMessageRetriever} so
     * that during the lifecycle of the spring container, it can be enabled and disabled at the same time that the {@link MessageBroker} is.
     *
     * @param messageRetriever the message retriever for this listener
     * @param messageBroker    the message broker that handles the processing of messages
     * @param messageResolver  the message resolver that will be used in this container
     */
    public SimpleMessageListenerContainer(final MessageRetriever messageRetriever,
                                          final MessageBroker messageBroker,
                                          final MessageResolver messageResolver) {
        this.messageRetriever = messageRetriever;
        this.messageBroker = messageBroker;
        this.messageResolver = messageResolver;

        this.executorService = null;
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    @Override
    public synchronized void start() {
        if (executorService != null) {
            return;
        }

        executorService = getNewExecutorService();

        if (messageRetriever instanceof AsyncMessageRetriever) {
            executorService.submit((AsyncMessageRetriever) messageRetriever);
        }

        if (messageResolver instanceof AsyncMessageResolver) {
            executorService.submit((AsyncMessageResolver) messageResolver);
        }

        messageBroker.start();
    }

    @Override
    public synchronized void stop() {
        if (executorService == null) {
            return;
        }

        try {
            executorService.shutdownNow();

            try {
                final Future<?> messageBrokerStoppedFuture = messageBroker.stop();

                messageBrokerStoppedFuture.get();
            } catch (final InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } catch (final ExecutionException executionException) {
                log.error("Error waiting for container to stop", executionException.getCause());
            }

            try {
                executorService.awaitTermination(1, TimeUnit.MINUTES);
            } catch (final InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            // Reset so we can start the container again in the future
            executorService = null;
        }
    }

    @VisibleForTesting
    ExecutorService getNewExecutorService() {
        return Executors.newCachedThreadPool();
    }
}
