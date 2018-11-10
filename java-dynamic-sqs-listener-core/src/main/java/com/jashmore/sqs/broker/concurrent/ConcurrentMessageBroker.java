package com.jashmore.sqs.broker.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.broker.AbstractMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.MessageProcessingException;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.ResizableSemaphore;
import com.jashmore.sqs.util.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Handles the processing of messages across a number of threads that can dynamically change during processing.
 */
@Slf4j
public class ConcurrentMessageBroker extends AbstractMessageBroker {
    private final MessageRetriever messageRetriever;
    private final MessageProcessor messageProcessor;
    private final ConcurrentMessageBrokerProperties properties;

    public ConcurrentMessageBroker(final MessageRetriever messageRetriever,
                                   final MessageProcessor messageProcessor,
                                   final ExecutorService executorService,
                                   final ConcurrentMessageBrokerProperties properties) {
        super(executorService);

        this.messageRetriever = messageRetriever;
        this.messageProcessor = messageProcessor;
        this.properties = properties;
    }

    @Override
    protected Controller createController(final CompletableFuture<Object> controllerFinishedFuture) {
        return new ConcurrentThreadController(messageRetriever, messageProcessor, properties, controllerFinishedFuture);
    }

    /**
     * Runnable that will be executed that handles the starting and stopping of threads that process messages.
     */
    @VisibleForTesting
    @RequiredArgsConstructor
    static class ConcurrentThreadController implements Controller {
        private final MessageRetriever messageRetriever;
        private final MessageProcessor messageProcessor;
        private final ConcurrentMessageBrokerProperties properties;

        /**
         * Executor that is used to start threads from the thread pool.
         *
         * <p>This is a specific {@link ThreadPoolExecutor} because when the controller is to be stopped we should wait for all of the currently running
         * threads to be stopped.
         */
        private final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, SECONDS, new SynchronousQueue<>());
        /**
         * Semaphore used to control the number of threads that are available to be run.
         *
         * <p>This is set to zero but will be replaced by the value from the {@link #properties}.
         */
        private final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(0);
        /**
         * Completable Future that should be resolved when this thread has fully finished processing.
         */
        private final CompletableFuture<Object> futureToResolve;
        /**
         * When shutting down this will determine whether any messages currently being processed should be interrupted.
         */
        private boolean interruptThreads;

        @Override
        @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE"})
        public void run() {
            boolean shouldShutdown = false;
            while (!shouldShutdown) {
                updateConcurrencyLevelIfChanged();

                final long numberOfMillisecondsToObtainPermit = properties.getPreferredConcurrencyPollingRateInMilliseconds();
                final Optional<Message> optionalMessage;
                boolean obtainedPermit = false;
                try {
                    // If the concurrency has changed to zero we will never be able to acquire a permit so we sleep before
                    // before we go and check if the concurrency level has changed again.
                    if (resizableSemaphore.getMaximumPermits() == 0) {
                        Thread.sleep(numberOfMillisecondsToObtainPermit);
                        continue;
                    }

                    final long timeStarting = System.currentTimeMillis();
                    obtainedPermit = resizableSemaphore.tryAcquire(numberOfMillisecondsToObtainPermit, MILLISECONDS);
                    if (!obtainedPermit) {
                        continue;
                    }

                    final long timePermitObtained = System.currentTimeMillis();

                    // We dynamically change the time allowed for retrieving a message based on the time it took to obtain
                    // a permit. This is so the polling rate for the concurrency level stays generally the correct amount
                    final long millisecondsToRetrieveMessage = Math.max(0, numberOfMillisecondsToObtainPermit - (timePermitObtained - timeStarting));

                    optionalMessage = messageRetriever.retrieveMessage(millisecondsToRetrieveMessage, MILLISECONDS);
                } catch (final InterruptedException interruptedException) {
                    log.info("Interrupted exception caught while adding more listeners, shutting down!");
                    shouldShutdown = true;
                    continue;
                } catch (final Throwable throwable) {
                    // We make sure that we release the permit if an exception occurred so that we don't end up with no
                    // available permits and no threads running
                    if (obtainedPermit) {
                        resizableSemaphore.release();
                    }
                    continue;
                }

                if (!optionalMessage.isPresent()) {
                    log.trace("No message retrieved for processing, trying again.");
                    resizableSemaphore.release();
                    continue;
                }

                executor.submit(() -> {
                    try {
                        messageProcessor.processMessage(optionalMessage.get());
                        log.trace("Message successfully processed removing from queue");
                    } catch (final MessageProcessingException messageProcessingException) {
                        log.error("Error processing message", messageProcessingException);
                    } finally {
                        resizableSemaphore.release();
                    }
                });
            }

            log.debug("Shutting down message controller");
            try {
                shutdownConcurrentThreads();
                log.debug("Message controller shut down");
            } catch (final Throwable throwable) {
                log.error("Exception thrown while waiting for broker to shutdown", throwable);
            } finally {
                futureToResolve.complete("DONE");
            }
        }

        /**
         * Checks the concurrency level of the broker and will update the number of threads that can be run concurrently if necessary.
         *
         * <p>If the concurrency level decreases any threads running currently will keep running.
         */
        private void updateConcurrencyLevelIfChanged() {
            final int newConcurrencyLevel = properties.getConcurrencyLevel();

            if (resizableSemaphore.getMaximumPermits() != newConcurrencyLevel) {
                log.debug("Changing concurrency from {} to {}", resizableSemaphore.getMaximumPermits(), newConcurrencyLevel);
                resizableSemaphore.changePermitSize(newConcurrencyLevel);
            }
        }

        /**
         * Shutdown the {@link #executor} and wait for all the threads to finish. If {@link #interruptThreads} is true the threads will be interrupted
         * during the shutdown.
         */
        private void shutdownConcurrentThreads() {
            if (interruptThreads) {
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
            while (!executor.isTerminated()) {
                log.debug("Waiting for all threads to finish...");
                try {
                    executor.awaitTermination(1, MINUTES);
                } catch (final InterruptedException interruptedException) {
                    log.warn("Interrupted while waiting for all messages to shutdown!");
                }
            }
        }

        @Override
        public void stopTriggered(final boolean shouldInterruptThreads) {
            this.interruptThreads = shouldInterruptThreads;
        }
    }
}
