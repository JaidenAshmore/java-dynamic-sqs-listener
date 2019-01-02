package com.jashmore.sqs.broker.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.broker.AbstractMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.MessageProcessingException;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.ResizableSemaphore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
         * Executor that is used to start threads from the thread pool for retrieving messages from the queue to be processed.
         *
         * <p>No threads that should actually process the messages should be built in this executor and this is instead the responsibility of the
         * {@link #messageProcessingThreadsExecutor}.
         */
        private final ExecutorService concurrentThreadsExecutor = Executors.newCachedThreadPool();

        /**
         * Executor that is used to start threads from the thread pool for processing messages that have been retrieved.
         *
         * <p>The reason for having this as a separate executor is so that when the broker is being shutdown we can interrupt the threads listening for messages
         * whilst still allowing for the processing of messages to continue.
         *
         * <p>This is a specific {@link ThreadPoolExecutor} because when the controller is to be stopped we should wait for all of the currently running
         * threads to be stopped.
         */
        private final ExecutorService messageProcessingThreadsExecutor = Executors.newCachedThreadPool();
        /**
         * Semaphore used to control the number of threads that are available to be run.
         *
         * <p>This is set to zero but will be replaced by the value from the {@link #properties}.
         */
        private final ResizableSemaphore concurrentMessagesBeingProcessedSemaphore = new ResizableSemaphore(0);
        /**
         * Completable Future that should be resolved when this thread has fully finished processing.
         */
        private final CompletableFuture<Object> futureToResolve;
        /**
         * When shutting down this will determine whether any messages currently being processed should be interrupted.
         */
        private boolean interruptThreads;

        /**
         * RV_RETURN_VALUE_IGNORED_BAD_PRACTICE is ignored because we don't actually care about the return future for submitting a thread to process a message.
         * Instead the {@link #concurrentMessagesBeingProcessedSemaphore} is used to control the number of concurrent threads and when we should down we
         * wait for the whole {@link ExecutorService} to finish and therefore we don't care about an individual thread.
         */
        @Override
        @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE"})
        public void run() {
            boolean shouldShutdown = false;
            while (!shouldShutdown) {
                updateConcurrencyLevelIfChanged();

                final long numberOfMillisecondsToObtainPermit = properties.getPreferredConcurrencyPollingRateInMilliseconds();
                try {
                    final boolean obtainedPermit = concurrentMessagesBeingProcessedSemaphore.tryAcquire(numberOfMillisecondsToObtainPermit, MILLISECONDS);
                    if (!obtainedPermit) {
                        continue;
                    }
                } catch (final InterruptedException interruptedException) {
                    log.info("Interrupted exception caught while adding more listeners, shutting down!");
                    shouldShutdown = true;
                    continue;
                }

                concurrentThreadsExecutor.submit(() -> {
                    final Future<?> messageProcessedFuture;
                    try {
                        final Message message;
                        try {
                            message = messageRetriever.retrieveMessage();
                        } catch (final InterruptedException exception) {
                            log.trace("Thread interrupted waiting for a message");
                            return;
                        }

                        messageProcessedFuture = concurrentThreadsExecutor.submit(() -> {
                            try {
                                messageProcessor.processMessage(message);
                                log.trace("Message successfully processed removing from queue");
                            } catch (final MessageProcessingException messageProcessingException) {
                                log.error("Error processing message", messageProcessingException);
                            } finally {
                                concurrentMessagesBeingProcessedSemaphore.release();
                            }
                        });
                    } catch (Throwable throwable) {
                        // We need to make sure the semaphore is released if there was a problem processing the message
                        log.error("Error thrown trying to retrieve a message for processing", throwable);
                        concurrentMessagesBeingProcessedSemaphore.release();
                        return;
                    }

                    try {
                        messageProcessedFuture.get();
                    } catch (InterruptedException exception) {
                        log.trace("Thread interrupted waiting for the message to be processed");
                        // do nothing, thread will exit
                    } catch (ExecutionException exception) {
                        log.error("Error processing message", exception);
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

            if (concurrentMessagesBeingProcessedSemaphore.getMaximumPermits() != newConcurrencyLevel) {
                log.debug("Changing concurrency from {} to {}", concurrentMessagesBeingProcessedSemaphore.getMaximumPermits(), newConcurrencyLevel);
                concurrentMessagesBeingProcessedSemaphore.changePermitSize(newConcurrencyLevel);
            }
        }

        /**
         * Shutdown the {@link #concurrentThreadsExecutor} and wait for all the threads to finish. If {@link #interruptThreads} is true the threads will
         * be interrupted during the shutdown.
         */
        private void shutdownConcurrentThreads() {
            concurrentThreadsExecutor.shutdownNow();
            if (interruptThreads) {
                messageProcessingThreadsExecutor.shutdownNow();
            } else {
                messageProcessingThreadsExecutor.shutdown();
            }
            while (!concurrentThreadsExecutor.isTerminated() && !messageProcessingThreadsExecutor.isTerminated()) {
                log.debug("Waiting for all threads to finish...");
                try {
                    concurrentThreadsExecutor.awaitTermination(1, MINUTES);
                    messageProcessingThreadsExecutor.awaitTermination(1, MINUTES);
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