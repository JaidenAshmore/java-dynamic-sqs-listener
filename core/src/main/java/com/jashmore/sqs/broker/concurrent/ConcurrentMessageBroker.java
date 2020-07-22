package com.jashmore.sqs.broker.concurrent;

import static com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerConstants.DEFAULT_BACKOFF_TIME;
import static com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerConstants.DEFAULT_CONCURRENCY_POLLING;
import static com.jashmore.sqs.util.properties.PropertyUtils.safelyGetPositiveOrZeroDuration;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.util.ResizableSemaphore;
import com.jashmore.sqs.util.properties.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Broker that will allow for messages to be processed concurrently up to a certain limit that can change dynamically.
 *
 * <p>The concurrency rate will be recalculated on each cycle which will increase decrease the number of available threads for processing. For example,
 * if the current concurrency rate is 5 and when it recalculates the concurrency it is now 6 it will allow another thread to process messages. However,
 * if the rate of concurrency decreases it will wait until a certain number of messages finishes processing messages before requesting more messages.
 *
 * <p>The current and max rate of concurrency is maintained via a {@link ResizableSemaphore} and this will be used to block the broker from processing
 * more messages than is desirable.  This rate is maintained over multiple calls to the process messages methods and therefore should not exceed the
 * desired concurrency rate even when calling {@link #processMessages(ExecutorService, BooleanSupplier, Supplier, Function)} multiple times sequentially.
 *
 * <p>Note that it may take a while for the concurrency rate to change based on whether all of the permits are currently being used, it will only recheck
 * the concurrency rate once another message is being used. The other way that the concurrency rate can be changed is if the request for a permit goes
 * over the desired length it will recalculate the concurrency and try again.
 *
 * @see ConcurrentMessageBrokerProperties for how to configure this broker
 */
@Slf4j
public class ConcurrentMessageBroker implements MessageBroker {
    private final ConcurrentMessageBrokerProperties properties;
    private final ResizableSemaphore concurrentMessagesBeingProcessedSemaphore;

    public ConcurrentMessageBroker(final ConcurrentMessageBrokerProperties properties) {
        this.properties = properties;
        this.concurrentMessagesBeingProcessedSemaphore = new ResizableSemaphore(0);
    }

    @Override
    public void processMessages(final ExecutorService messageProcessingExecutorService,
                                final BooleanSupplier keepProcessingMessages,
                                final Supplier<CompletableFuture<Message>> messageSupplier,
                                final Function<Message, CompletableFuture<?>> messageProcessor) throws InterruptedException {
        log.debug("Beginning processing of messages");
        while (!Thread.currentThread().isInterrupted() && keepProcessingMessages.getAsBoolean()) {
            try {
                updateConcurrencyLevelIfChanged(concurrentMessagesBeingProcessedSemaphore);

                final Duration permitWaitTime = getPermitWaitTime();
                final boolean obtainedPermit = concurrentMessagesBeingProcessedSemaphore.tryAcquire(permitWaitTime.toMillis(), MILLISECONDS);
                if (!obtainedPermit) {
                    continue;
                }

                try {
                    messageSupplier.get()
                            .thenComposeAsync(messageProcessor::apply, messageProcessingExecutorService)
                            .whenComplete((ignoredResult, throwable) -> {
                                if (throwable != null && !(throwable.getCause() instanceof CancellationException)) {
                                    log.error("Error processing message", throwable.getCause());
                                }
                                concurrentMessagesBeingProcessedSemaphore.release();
                            });
                } catch (final RuntimeException runtimeException) {
                    concurrentMessagesBeingProcessedSemaphore.release();
                    // bubble the exception to deal with backing off, as we don't want to duplicate that code
                    throw runtimeException;
                }
            } catch (final RuntimeException runtimeException) {
                final Duration errorBackoffTime = safelyGetPositiveOrZeroDuration("errorBackoffTime", properties::getErrorBackoffTime, DEFAULT_BACKOFF_TIME);
                final long errorBackoffTimeInMs = errorBackoffTime.toMillis();
                log.error("Error thrown while organising threads to process messages. Backing off for {}ms", errorBackoffTimeInMs,
                        runtimeException);
                Thread.sleep(errorBackoffTimeInMs);
            }
        }
        log.debug("Ending processing of messages");
    }

    /**
     * Safely get the number of milliseconds that should wait to get a permit for creating a new thread.
     *
     * @return the durationToWaitFor
     * @see ConcurrentMessageBrokerProperties#getConcurrencyPollingRate() for more information
     */
    private Duration getPermitWaitTime() {
        final Duration pollingRate = properties.getConcurrencyPollingRate();
        if (pollingRate != null && !pollingRate.isNegative()) {
            return pollingRate;
        }

        return DEFAULT_CONCURRENCY_POLLING;
    }

    /**
     * Checks the concurrency level of the broker and will update the number of threads that can be run concurrently if necessary.
     *
     * <p>If the concurrency level decreases any threads running currently will keep running.
     */
    private void updateConcurrencyLevelIfChanged(final ResizableSemaphore resizableSemaphore) {
        final int newConcurrencyLevel = getConcurrencyLevel();

        if (resizableSemaphore.getMaximumPermits() != newConcurrencyLevel) {
            log.info("Changing concurrency from {} to {}", resizableSemaphore.getMaximumPermits(), newConcurrencyLevel);
            resizableSemaphore.changePermitSize(newConcurrencyLevel);
        }
    }

    /**
     * Determine the concurrency level safely, returning zero if there was an error or the value was negative.
     *
     * @return the expected concurrency level
     */
    private int getConcurrencyLevel() {
        return PropertyUtils.safelyGetPositiveOrZeroIntegerValue(
                "concurrencyLevel",
                properties::getConcurrencyLevel,
                0
        );
    }
}
