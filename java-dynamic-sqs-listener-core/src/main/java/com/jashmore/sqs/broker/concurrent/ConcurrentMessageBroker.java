package com.jashmore.sqs.broker.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.ResizableSemaphore;
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
        final ExecutorService concurrentThreadsExecutor = Executors.newCachedThreadPool();
        final ExecutorService messageProcessingThreadsExecutor = Executors.newCachedThreadPool();
        final ResizableSemaphore concurrentMessagesBeingProcessedSemaphore = new ResizableSemaphore(0);

        while (!Thread.currentThread().isInterrupted()) {
            updateConcurrencyLevelIfChanged(concurrentMessagesBeingProcessedSemaphore);

            final long numberOfMillisecondsToObtainPermit = properties.getPreferredConcurrencyPollingRateInMilliseconds();
            try {
                final boolean obtainedPermit = concurrentMessagesBeingProcessedSemaphore.tryAcquire(numberOfMillisecondsToObtainPermit, MILLISECONDS);
                if (!obtainedPermit) {
                    continue;
                }
            } catch (final InterruptedException interruptedException) {
                log.info("Interrupted exception caught while adding more listeners, shutting down!");
                Thread.currentThread().interrupt();
                continue;
            }

            CompletableFuture.supplyAsync(() -> {
                try {
                    return messageRetriever.retrieveMessage();
                } catch (final InterruptedException exception) {
                    log.trace("Thread interrupted waiting for a message");
                    throw new RuntimeException("Failure to get message");
                }
            }, concurrentThreadsExecutor)
                    .thenAcceptAsync(messageProcessor::processMessage, messageProcessingThreadsExecutor)
                    .whenComplete((ignoredResult, throwable) -> concurrentMessagesBeingProcessedSemaphore.release());
        }

        log.debug("Shutting down message controller");
        try {
            shutdownConcurrentThreads(concurrentThreadsExecutor, messageProcessingThreadsExecutor);
            log.debug("Message controller shut down");
        } catch (final Throwable throwable) {
            log.error("Exception thrown while waiting for broker to shutdown", throwable);
        }
    }

    /**
     * Checks the concurrency level of the broker and will update the number of threads that can be run concurrently if necessary.
     *
     * <p>If the concurrency level decreases any threads running currently will keep running.
     */
    private void updateConcurrencyLevelIfChanged(final ResizableSemaphore resizableSemaphore) {
        final int newConcurrencyLevel = properties.getConcurrencyLevel();

        if (resizableSemaphore.getMaximumPermits() != newConcurrencyLevel) {
            log.debug("Changing concurrency from {} to {}", resizableSemaphore.getMaximumPermits(), newConcurrencyLevel);
            resizableSemaphore.changePermitSize(newConcurrencyLevel);
        }
    }

    private void shutdownConcurrentThreads(final ExecutorService concurrentThreadsExecutor,
                                           final ExecutorService messageProcessingThreadsExecutor) {
        concurrentThreadsExecutor.shutdownNow();
        messageProcessingThreadsExecutor.shutdown();
        while (!concurrentThreadsExecutor.isTerminated() || !messageProcessingThreadsExecutor.isTerminated()) {
            log.debug("Waiting for all threads to finish...");
            try {
                concurrentThreadsExecutor.awaitTermination(1, MINUTES);
                messageProcessingThreadsExecutor.awaitTermination(1, MINUTES);
            } catch (final InterruptedException interruptedException) {
                log.warn("Interrupted while waiting for all messages to shutdown!");
            }
        }
    }
}