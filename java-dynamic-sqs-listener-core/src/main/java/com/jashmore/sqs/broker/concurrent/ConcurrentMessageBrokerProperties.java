package com.jashmore.sqs.broker.concurrent;

import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.retriever.MessageRetriever;

import javax.annotation.concurrent.NotThreadSafe;
import javax.validation.constraints.Min;

/**
 * Properties for dynamically configuring how the {@link ConcurrentMessageBroker} is able to process messages concurrently.
 *
 * <p>These properties will be consumed by the {@link ConcurrentMessageBroker} at a high rate (every time a new message is needed) and therefore considerations
 * should be considered in terms of the performance of this. If obtaining these values are costly, for example calling out to an external system, it is
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
     * If there is currently less threads running than this value, more threads will to meet this value. If there are more threads running
     * than this value, this coordinating thread will block until the amount of concurrent threads has gone below this value or the a certain
     * amount of time, defined by {@link #getPreferredConcurrencyPollingRateInMilliseconds()}, has passed. In the case that the polling period is reached,
     * the coordinating thread will do this process again.
     *
     * <p>Note that the concurrency rate does not get applied instantly and there are multiple attributing factors for when the rate of concurrency will
     * actually transition to a new concurrency rate when this value changes, for example:
     * <ul>
     *     <li>
     *         The rate of concurrency may take a while to decrease due to no messages being taken from the queue. Due to internal
     *         implementation details of the {@link ConcurrentMessageBroker} it will spin up as many threads as the concurrency level where each will wait
     *         for a message to be retrieved. These threads will not be stopped until a message is taken and processed. Therefore if you have a concurrency
     *         level of 10, 10 threads will be started all waiting for messages and even though the concurrency rate may decrease to 5, this concurrency rate
     *         will not decrease until 5 of those 10 threads process a message.  The reason for this is to a) decrease the complexity of the codebase and
     *         b) we don't know the internal details of the {@link MessageRetriever} and therefore requesting a message but then never using it because
     *         the thread got interrupted may result in lost messages requiring reprocessing via a re-drive policy.
     *     </li>
     *     <li>
     *         The delay in the concurrency rate change may be due to transitioning from allowing zero threads to a number of threads. In a worst
     *         case scenario there would be a delay of {@link #getPreferredConcurrencyPollingRateInMilliseconds()} before the rate of concurrency is changed.
     *     </li>
     * </ul>
     *
     * @return the the level of concurrency for processing messages
     */
    @Min(0)
    Integer getConcurrencyLevel();

    /**
     * The number of milliseconds that the coordinating thread will sleep when the maximum rate of concurrency has been reached before checking again.
     *
     * <p>The reason that this property is needed is because during the processing of messages, which could be a significantly long period, the rate
     * of concurrency may have changed, more specifically increased. To make sure that more threads are spun up as required, the coordinating
     * thread will block for this period of time and once it is awoken it will check to see if the concurrency level has changed. If it has, more threads
     * can be spun up to process more messages. However, if the current number of threads is equal to or greater than the desired amount the coordinating
     * thread will go back to sleep for this period of time or until the number of threads goes below this number.
     *
     * <p>There are performance considerations when determine an appropriate value for this property in that a higher polling period will result in less time
     * that the coordinating thread is awoken and is therefore less CPU intensive. However, decreasing this polling period makes it more responsive to changes
     * to the rate of concurrency.
     *
     * @return the number of milliseconds between polls for the concurrency level
     */
    @Min(0)
    Integer getPreferredConcurrencyPollingRateInMilliseconds();
}
