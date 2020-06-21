package com.jashmore.sqs.broker.concurrent;

import com.jashmore.documentation.annotations.NotThreadSafe;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.PositiveOrZero;

/**
 * Properties for dynamically configuring how the {@link ConcurrentMessageBroker} is able to process messages concurrently.
 *
 * <p>These properties will be consumed by the {@link ConcurrentMessageBroker} at a high rate (every time a new message is needed) and therefore the
 * performance of this implementation should be considered. If obtaining these values are costly, for example calling out to an external system, it is
 * recommended to cache the values for a period of time so it is not making calls every time a message needed therefore significantly impacting the throughput
 * of this broker.
 *
 * <p>Implementations of these properties do not need to be thread safe because there is only a single coordinating thread that will be consuming this
 * object.
 */
@NotThreadSafe
public interface ConcurrentMessageBrokerProperties {
    /**
     * The level of concurrency for processing messages, e.g. the number of threads that can process messages at the same time. This value can change
     * dynamically throughout the usage of this broker which can be useful if you want to provide the rate of concurrency behind a feature flag or
     * configuration in the application.
     *
     * <p>The {@link ConcurrentMessageBroker} will maintain the rate of concurrency by checking that the current concurrency rate is aligned with this value.
     * If there is currently less messages being processed than this value, more requests for messages will be made to meet this value. If there are
     * more messages being processed or requested than this value, this coordinating thread will block until enough messages have been processed.  If a
     * permit has not be obtained before the timeout defined by {@link #getConcurrencyPollingRateInMilliseconds()}, it will recalculate this
     * concurrency rate again and wait for a permit.
     *
     * <p>Note that the concurrency rate does not get applied instantly and there are multiple attributing factors for when the rate of concurrency will
     * actually transition to a new concurrency rate when this value changes, for example:
     * <ul>
     *     <li>
     *         The rate of concurrency may take a while to decrease due to no messages being taken from the queue. Due to internal
     *         implementation details of the {@link ConcurrentMessageBroker} it will request as many messages as the concurrency level and will wait until
     *         another slot is available for a message to be processed before recalculating the concurrency. For example, if the concurrency rate was 10,
     *         10 messages will be requested and even though the concurrency rate may have decreased to 5, this concurrency rate
     *         will not decrease until 6 of those 10 threads have finished process a message and another can be requested.
     *     </li>
     *     <li>
     *         The delay in the concurrency rate change may be due to transitioning from allowing zero threads to a number of threads. In a worst
     *         case scenario there would be a delay of {@link #getConcurrencyPollingRateInMilliseconds()} before the rate of concurrency is changed.
     *     </li>
     * </ul>
     *
     * @return the the level of concurrency for processing messages
     */
    @PositiveOrZero
    int getConcurrencyLevel();

    /**
     * The number of milliseconds that the coordinating thread will sleep when the maximum rate of concurrency has been reached before checking the
     * concurrency rate again.
     *
     * <p>The reason that this property is needed is because during the processing of messages, which could be a significantly long period, the rate
     * of concurrency may have changed. The coordinating background thread should periodically check the concurrency rate  while messages are processing
     * so that it can request more messages to be processed if that concurrency rate has increased. In the case that messages take a long time, there would
     * be a decrease in performance as it could have been processing more messages during this period.
     *
     * <p>There are performance considerations when determine an appropriate value for this property in that a higher polling period will result in less time
     * that the coordinating thread is awoken and is therefore less CPU intensive. However, decreasing this polling period makes it more responsive to changes
     * to the rate of concurrency.
     *
     * <p>If this value is null or less than zero, {@link ConcurrentMessageBrokerConstants#DEFAULT_CONCURRENCY_POLLING_IN_MS} will be used instead.
     *
     * @return the number of milliseconds between polls for the concurrency level
     */
    @Nullable
    @PositiveOrZero
    Long getConcurrencyPollingRateInMilliseconds();

    /**
     * The number of milliseconds that the coordinating thread should backoff if there was an error trying to request a message.
     *
     * <p>This is needed to stop the background thread from trying again and again over and over causing a flood of error log messages that may make it
     * difficult to debug.
     *
     * <p>If this value is null or negative, {@link ConcurrentMessageBrokerConstants#DEFAULT_BACKOFF_TIME_IN_MS} will be used as the backoff period.
     *
     * @return the number of milliseconds to sleep the thread after an error is thrown
     */
    @Nullable
    @PositiveOrZero
    Long getErrorBackoffTimeInMilliseconds();
}
