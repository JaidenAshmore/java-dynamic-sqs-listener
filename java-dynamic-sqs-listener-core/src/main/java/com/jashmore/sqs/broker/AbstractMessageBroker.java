package com.jashmore.sqs.broker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Abstract {@link MessageBroker} that handles the starting and stopping of the background broker thread.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractMessageBroker implements MessageBroker {
    private final ExecutorService executorService;

    private Controller controller;
    private Future<?> controllerFuture;
    private CompletableFuture<Object> brokerStoppedFuture;

    @Override
    public synchronized void start() {
        log.debug("Starting broker");
        if (controller != null) {
            log.warn("{} is already running", this.getClass().getSimpleName());
            return;
        }
        final CompletableFuture<Object> brokerFuture = new CompletableFuture<>();
        brokerStoppedFuture = brokerFuture;
        log.debug("Started listening");
        controller = createController(brokerFuture);
        controllerFuture = executorService.submit(controller);
    }

    @Override
    public synchronized Future<?> stop(final boolean interruptThreads) {
        log.debug("Stopping broker");
        if (controller == null) {
            log.warn("ConcurrentMessageListenerContainer is not running");
            return CompletableFuture.completedFuture("Not running");
        }

        controller.stopTriggered(interruptThreads);
        controllerFuture.cancel(true);
        final Future<?> futureToReturn = brokerStoppedFuture;
        brokerStoppedFuture = null;
        controller = null;
        controllerFuture = null;
        return futureToReturn;
    }

    /**
     * Create the {@link Controller} that will be running in the background for this {@link MessageBroker}.
     *
     * @param controllerFinishedFuture the future that will be resolved when the {@link Controller#run()} has finished
     * @return the {@link Controller} that will be running in background
     */
    protected abstract Controller createController(final CompletableFuture<Object> controllerFinishedFuture);

    /**
     * Class that will be doing all of the processing for the {@link MessageBroker} in a background thread.
     */
    public interface Controller extends Runnable {
        /**
         * Indicate that the {@link MessageBroker} is being stopped and this is used to indicate when this controller is stopping whethere
         * the threads processing the messages should be interrupted or not.
         *
         * @param shouldInterruptThreads whether message processing threads should be interrupted on shutdown
         */
        void stopTriggered(boolean shouldInterruptThreads);
    }
}
