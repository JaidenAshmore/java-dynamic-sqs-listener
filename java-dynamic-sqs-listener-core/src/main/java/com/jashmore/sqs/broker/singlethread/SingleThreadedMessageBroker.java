package com.jashmore.sqs.broker.singlethread;

import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.broker.AbstractMessageBroker;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Single threaded implementation of the {@link MessageBroker}.
 *
 * <p>Note that the {@link ConcurrentMessageBroker} with a concurrency level of one could have been used
 * but there would have been an extra thread checking to see whether the concurrency rate has changed (which it never will)
 * and therefore is wasted cycles. This implementation therefore can be simpler and less prone to errors.
 */
@Slf4j
public class SingleThreadedMessageBroker extends AbstractMessageBroker {
    private final MessageRetriever messageRetriever;
    private final MessageProcessor messageProcessor;
    private final ExecutorService executorService;

    public SingleThreadedMessageBroker(final MessageRetriever messageRetriever, final MessageProcessor messageProcessor) {
        this(messageRetriever, messageProcessor, Executors.newFixedThreadPool(2));
    }

    @VisibleForTesting
    SingleThreadedMessageBroker(final MessageRetriever messageRetriever,
                                final MessageProcessor messageProcessor,
                                final ExecutorService executorService) {
        super(executorService);

        this.messageRetriever = messageRetriever;
        this.messageProcessor = messageProcessor;
        this.executorService = executorService;
    }

    @Override
    protected SingleThreadMessageController createController(final CompletableFuture<Object> controllerFinishedFuture) {
        return new SingleThreadMessageController(messageRetriever, messageProcessor, executorService, controllerFinishedFuture);
    }

    /**
     * This is the controller that will handle the retrieval of messages and the subsequent processing.
     *
     * <p>This will only be run on a single thread and any message that is processed will be started on a new thread. The reason for starting it on a new
     * thread is because we don't want interrupts to cause the processing of the message to be stopped. If the thread is interrupted it will wait until the
     * message is processed for stopping the thread.
     */
    @Slf4j
    @RequiredArgsConstructor
    static class SingleThreadMessageController  implements AbstractMessageBroker.Controller {
        /**
         * The amount of time that the thread should be slept if there was an error retrieving messages for processing.
         */
        private static final int BACKOFF_TIME_MS = 1000;

        private final MessageRetriever messageRetriever;
        private final MessageProcessor messageProcessor;
        private final ExecutorService executorService;
        private final CompletableFuture<Object> controllerFinishedFuture;

        /**
         * When shutting down this will determine whether any messages currently being processed should be interrupted.
         */
        private boolean interruptThreads;

        @Override
        public void run() {
            log.debug("Starting message listener");
            Future<?> currentlyRunningThread = null;
            boolean shouldStop = false;
            while (!shouldStop) {
                final Message message;
                try {
                    message = messageRetriever.retrieveMessage();

                    currentlyRunningThread = executorService.submit(() -> messageProcessor.processMessage(message));

                    currentlyRunningThread.get();
                } catch (final InterruptedException interruptedException) {
                    log.info("Thread has been interrupted. Stopping listener");
                    shouldStop = true;
                } catch (final Throwable throwable) {
                    log.error("Exception thrown while retrieving a message to process. Backing off for 1000ms", throwable);
                    currentlyRunningThread = null;
                    try {
                        backoff();
                    } catch (final InterruptedException interruptedException) {
                        log.info("Thread has been interrupted during the backoff. Stopping listener");
                        shouldStop = true;
                    }
                }
            }

            if (currentlyRunningThread != null) {
                if (interruptThreads) {
                    currentlyRunningThread.cancel(true);
                }

                try {
                    currentlyRunningThread.get();
                } catch (final InterruptedException interruptedException) {
                    log.error("Thread interrupted again before waiting for message to be processed. Some messages may not have been fully completed",
                            interruptedException);
                } catch (final ExecutionException executionException) {
                    log.error("Error waiting for message to be processed", executionException.getCause());
                }
            }

            log.info("Message listener shutdown");
            controllerFinishedFuture.complete("DONE");
        }

        /**
         * Sleep the thread for a certain period of time so that this thread is not constantly spinning and throwing errors.
         *
         * @throws InterruptedException if the thread was interrupted while sleeping
         */
        @VisibleForTesting
        void backoff() throws InterruptedException {
            Thread.sleep(BACKOFF_TIME_MS);
        }

        @Override
        public void stopTriggered(final boolean shouldInterruptThreads) {
            this.interruptThreads = shouldInterruptThreads;
        }
    }
}
