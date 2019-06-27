package com.jashmore.sqs.broker.concurrent;

import static com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerConstants.DEFAULT_BACKOFF_TIME_IN_MS;
import static com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerConstants.DEFAULT_SHUTDOWN_TIME_IN_SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.ResizableSemaphore;
import com.jashmore.sqs.util.properties.PropertyUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles the processing of messages across a number of threads that can dynamically change during processing.
 */
@Slf4j
public class ConcurrentMessageBroker implements MessageBroker {
    private final MessageRetriever messageRetriever;
    private final MessageProcessor messageProcessor;
    private final ConcurrentMessageBrokerProperties properties;

    public ConcurrentMessageBroker(final MessageRetriever messageRetriever,
                                   final MessageProcessor messageProcessor,
                                   final ConcurrentMessageBrokerProperties properties) {
        this.messageRetriever = messageRetriever;
        this.messageProcessor = messageProcessor;
        this.properties = properties;
    }

    /**
     * RV_RETURN_VALUE_IGNORED_BAD_PRACTICE is ignored because we don't actually care about the return future for submitting a thread to process a message.
     * Instead the {@link ResizableSemaphore} is used to control the number of concurrent threads and when we should down we
     * wait for the whole {@link ExecutorService} to finish and therefore we don't care about an individual thread.
     */
    @Override
    @SuppressFBWarnings( {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE"})
    public void run() {
        log.info("Started ConcurrentMessageBroker background thread");
        final ExecutorService concurrentThreadsExecutor = Executors.newCachedThreadPool();
        final ExecutorService messageProcessingThreadsExecutor = buildMessageProcessingExecutorService();
        final ResizableSemaphore concurrentMessagesBeingProcessedSemaphore = new ResizableSemaphore(0);

        while (true) {
            try {
                updateConcurrencyLevelIfChanged(concurrentMessagesBeingProcessedSemaphore);

                final long numberOfMillisecondsToObtainPermit = getNumberOfMillisecondsToObtainPermit();
                try {
                    final boolean obtainedPermit = concurrentMessagesBeingProcessedSemaphore.tryAcquire(numberOfMillisecondsToObtainPermit, MILLISECONDS);
                    if (!obtainedPermit) {
                        continue;
                    }
                } catch (final InterruptedException interruptedException) {
                    log.debug("Interrupted exception caught while adding more listeners, shutting down!");
                    break;
                }

                CompletableFuture.supplyAsync(() -> {
                    try {
                        return messageRetriever.retrieveMessage();
                    } catch (final InterruptedException exception) {
                        log.trace("Thread interrupted waiting for a message");
                        throw new BrokerStoppedWhileRetrievingMessageException();
                    }
                }, concurrentThreadsExecutor)
                        .thenAcceptAsync(messageProcessor::processMessage, messageProcessingThreadsExecutor)
                        .whenComplete((ignoredResult, throwable) -> {
                            if (throwable != null && !(throwable.getCause() instanceof BrokerStoppedWhileRetrievingMessageException)) {
                                log.error("Error processing message", throwable.getCause());
                            }
                            concurrentMessagesBeingProcessedSemaphore.release();
                        });
            } catch (final Throwable throwable) {
                try {
                    final long errorBackoffTimeInMilliseconds = getErrorBackoffTimeInMilliseconds();
                    log.error("Error thrown while organising threads to process messages. Backing off for {}ms", errorBackoffTimeInMilliseconds, throwable);
                    Thread.sleep(errorBackoffTimeInMilliseconds);
                } catch (final InterruptedException interruptedException) {
                    log.debug("Thread interrupted during backoff period");
                    break;
                }
            }
        }

        log.info("ConcurrentMessageBroker background thread shutting down...");
        try {
            shutdownConcurrentThreads(concurrentThreadsExecutor, messageProcessingThreadsExecutor);
            log.info("ConcurrentMessageBroker background thread successfully stopped");
        } catch (final RuntimeException runtimeException) {
            log.error("Exception thrown while waiting for broker to shutdown", runtimeException);
        }
    }

    /**
     * Checks the concurrency level of the broker and will update the number of threads that can be run concurrently if necessary.
     *
     * <p>If the concurrency level decreases any threads running currently will keep running.
     */
    private void updateConcurrencyLevelIfChanged(final ResizableSemaphore resizableSemaphore) {
        final int newConcurrencyLevel = properties.getConcurrencyLevel();
        Preconditions.checkArgument(newConcurrencyLevel >= 0, "concurrencyLevel should be non-negative");

        if (resizableSemaphore.getMaximumPermits() != newConcurrencyLevel) {
            log.debug("Changing concurrency from {} to {}", resizableSemaphore.getMaximumPermits(), newConcurrencyLevel);
            resizableSemaphore.changePermitSize(newConcurrencyLevel);
        }
    }

    /**
     * Shutdown all of the concurrent threads for retrieving messages by interrupting it but let any threads processing messages gracefully shutdown.
     *
     * <p>This method is visible for testing due to the difficulty in testing this.
     *
     * @param concurrentThreadsExecutor        the executor for retrieving messages
     * @param messageProcessingThreadsExecutor the executor processing messages downloaded
     */
    @VisibleForTesting
    void shutdownConcurrentThreads(final ExecutorService concurrentThreadsExecutor,
                                   final ExecutorService messageProcessingThreadsExecutor) {
        concurrentThreadsExecutor.shutdownNow();
        if (properties.shouldInterruptThreadsProcessingMessagesOnShutdown()) {
            messageProcessingThreadsExecutor.shutdownNow();
        } else {
            messageProcessingThreadsExecutor.shutdown();
        }
        log.debug("Waiting for all threads to finish...");
        try {
            final long shutdownTimeoutInSeconds = getShutdownTimeoutInSeconds();
            final long timeNow = System.currentTimeMillis();
            final boolean concurrentThreadsShutdown = concurrentThreadsExecutor.awaitTermination(SECONDS.toMillis(shutdownTimeoutInSeconds), MILLISECONDS);
            final long leftOverShutdownTimeoutInMilliseconds = System.currentTimeMillis() - timeNow;
            final boolean messageProcessingThreadsShutdown = messageProcessingThreadsExecutor.awaitTermination(
                    leftOverShutdownTimeoutInMilliseconds, MILLISECONDS);
            if (!concurrentThreadsShutdown || !messageProcessingThreadsShutdown) {
                log.error("Message processing threads did not shutdown within {} seconds", shutdownTimeoutInSeconds);
            }
        } catch (final InterruptedException interruptedException) {
            log.warn("Interrupted while waiting for all messages to shutdown, some threads may still be running");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Build the {@link ExecutorService} that will be used for the threads that are processing the messages.
     *
     * <p>This will provide the logic to name the threads to make it easier to debug multiple different message listeners in the system.
     *
     * @return the executor service that will be used for processing messages
     */
    private ExecutorService buildMessageProcessingExecutorService() {
        try {
            final ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder();

            final String threadNameFormat = properties.getThreadNameFormat();
            if (threadNameFormat != null) {
                threadFactoryBuilder.setNameFormat(threadNameFormat);
            }

            return Executors.newCachedThreadPool(threadFactoryBuilder.build());
        } catch (final Throwable throwable) {
            log.error("Error thrown building message processing executor service, returning default");
            return Executors.newCachedThreadPool();
        }
    }

    /**
     * Get the number of seconds that the thread should wait when there was an error trying to organise a thread to process.
     *
     * @return the backoff time in milliseconds
     * @see ConcurrentMessageBrokerProperties#getErrorBackoffTimeInMilliseconds() for more information
     */
    private long getErrorBackoffTimeInMilliseconds() {
        return PropertyUtils.safelyGetPositiveOrZeroLongValue(
                "errorBackoffTimeInMilliseconds",
                properties::getErrorBackoffTimeInMilliseconds,
                DEFAULT_BACKOFF_TIME_IN_MS
        );
    }

    /**
     * Get the amount of time in seconds that we should wait for the server to shutdown.
     *
     * @return the amount of time in seconds to wait for shutdown
     */
    private long getShutdownTimeoutInSeconds() {
        return PropertyUtils.safelyGetPositiveOrZeroLongValue(
                "shutdownTimeoutInSeconds",
                properties::getShutdownTimeoutInSeconds,
                DEFAULT_SHUTDOWN_TIME_IN_SECONDS
        );
    }

    /**
     * Safely get the number of milliseconds that should wait to get a permit for creating a new thread.
     *
     * @return the number of milliseconds to wait
     * @see ConcurrentMessageBrokerProperties#getPreferredConcurrencyPollingRateInMilliseconds() for more information
     */
    private long getNumberOfMillisecondsToObtainPermit() {
        return PropertyUtils.safelyGetPositiveLongValue(
                "preferredConcurrencyPollingRateInMilliseconds",
                properties::getPreferredConcurrencyPollingRateInMilliseconds,
                DEFAULT_BACKOFF_TIME_IN_MS
        );
    }

    /**
     * Internal exception used to be thrown when the thread is interrupted while retrieving messages. This is because we don't want a
     * error to be logged for this scenario but only when their was an actual exception processing the message.
     */
    private static class BrokerStoppedWhileRetrievingMessageException extends RuntimeException {

    }
}