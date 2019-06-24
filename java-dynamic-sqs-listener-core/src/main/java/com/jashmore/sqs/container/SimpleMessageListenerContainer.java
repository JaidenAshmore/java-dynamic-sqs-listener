package com.jashmore.sqs.container;

import static com.jashmore.sqs.container.SimpleMessageListenerContainerConstants.DEFAULT_SHUTDOWN_RETRY_AMOUNT;
import static com.jashmore.sqs.container.SimpleMessageListenerContainerConstants.DEFAULT_SHUTDOWN_TIMEOUT;
import static com.jashmore.sqs.container.SimpleMessageListenerContainerConstants.DEFAULT_SHUTDOWN_TIMEOUT_TIME_UNIT;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.AsyncMessageResolver;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Simple container that will start and stop all of the components of the background thread.
 */
@Slf4j
@ThreadSafe
public class SimpleMessageListenerContainer implements MessageListenerContainer {
    /**
     * The identifier for this container.
     *
     * <p>This is helpful to distinguish the logs of this container.
     */
    private final String identifier;

    /**
     * The {@link MessageRetriever} that will be used in this container to obtain messages to process.
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

    /**
     * Properties for configuring this container.
     */
    private final SimpleMessageListenerContainerProperties properties;

    @GuardedBy("this")
    private ExecutorService executorService;

    /**
     * Container that can be built when the {@link MessageBroker} is using an {@link AsyncMessageRetriever}. This takes the {@link AsyncMessageRetriever} so
     * that during the lifecycle of the spring container, it can be enabled and disabled at the same time that the {@link MessageBroker} is.
     *
     * @param identifier       the identifier for this container
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
        this.properties = StaticSimpleMessageListenerContainerProperties.builder()
                .shutdownTimeout(DEFAULT_SHUTDOWN_TIMEOUT)
                .shutdownTimeUnit(DEFAULT_SHUTDOWN_TIMEOUT_TIME_UNIT)
                .shutdownRetryLimit(DEFAULT_SHUTDOWN_RETRY_AMOUNT)
                .build();

        this.executorService = null;
    }

    public SimpleMessageListenerContainer(final String identifier,
                                          final MessageRetriever messageRetriever,
                                          final MessageBroker messageBroker,
                                          final MessageResolver messageResolver,
                                          final SimpleMessageListenerContainerProperties properties) {
        this.identifier = identifier;
        this.messageRetriever = messageRetriever;
        this.messageBroker = messageBroker;
        this.messageResolver = messageResolver;
        this.properties = properties;

        this.executorService = null;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    @Override
    public synchronized void start() {
        log.info("Container({}) is being started", identifier);
        if (executorService != null) {
            log.debug("Container has already been started. No action taken");
            return;
        }

        executorService = getNewExecutorService();

        if (messageRetriever instanceof AsyncMessageRetriever) {
            executorService.submit((AsyncMessageRetriever) messageRetriever);
        }

        if (messageResolver instanceof AsyncMessageResolver) {
            executorService.submit((AsyncMessageResolver) messageResolver);
        }

        executorService.submit(messageBroker);
        log.info("Container({}) successfully started", identifier);
    }

    @Override
    public synchronized void stop() {
        log.info("Container({}) is being stopped", identifier);
        if (executorService == null) {
            log.debug("Container({}) has already been stopped. No action taken", identifier);
            return;
        }

        try {
            final boolean didServiceShutdown = stopBackgroundThreadsWithRetries(properties.getShutdownRetryLimit());
            if (didServiceShutdown) {
                log.info("Container({}) successfully stopped", identifier);
            } else {
                log.error("Container({}) did not stop. There may be background threads still running", identifier);
            }
        } finally {
            // Reset so we can start the container again in the future
            executorService = null;
        }
    }

    private boolean stopBackgroundThreadsWithRetries(int numberOfRetriesLeft) {
        executorService.shutdownNow();

        try {
            final long timeout = properties.getShutdownTimeout();
            final TimeUnit timeUnit = properties.getShutdownTimeUnit();
            final boolean isTerminated = executorService.awaitTermination(timeout, timeUnit);
            if (isTerminated) {
                return true;
            }

            if (numberOfRetriesLeft <= 0) {
                return false;
            } else {
                log.warn("Container({}) has not stopped in {} {}, trying again", identifier, timeout, timeUnit);
                return stopBackgroundThreadsWithRetries(numberOfRetriesLeft - 1);
            }
        } catch (final InterruptedException interruptedException) {
            log.warn("Container({}) shutdown was interrupted and may not have shutdown all threads", identifier);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @VisibleForTesting
    ExecutorService getNewExecutorService() {
        return Executors.newCachedThreadPool();
    }
}
