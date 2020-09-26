package com.jashmore.sqs.broker.grouping;

import com.jashmore.documentation.annotations.NotThreadSafe;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerConstants;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import java.time.Duration;
import java.util.function.Function;
import org.immutables.value.Value;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

/**
 * Properties for dynamically configuring how the {@link GroupingMessageBroker} is able to process messages concurrently.
 *
 * <p>These properties will be consumed by the {@link GroupingMessageBroker} at a high rate (every time a new message is needed) and therefore the
 * performance of this implementation should be considered. If obtaining these values are costly, for example calling out to an external system, it is
 * recommended to cache the values for a period of time so it is not making calls every time a message needed therefore significantly impacting the throughput
 * of this broker.
 *
 * <p>Implementations of these properties do not need to be thread safe because the {@link GroupingMessageBroker} will guarantee that they are not called
 * concurrently.
 */
@NotThreadSafe
@Value.Immutable
public interface GroupingMessageBrokerProperties {
    /**
     * The level of concurrency for processing messages, e.g. the number of threads that can process messages at the same time. This value can change
     * dynamically throughout the usage of this broker which can be useful if you want to provide the rate of concurrency behind a feature flag or
     * configuration in the application.
     *
     * <p>The {@link GroupingMessageBroker} will maintain the rate of concurrency by checking that the current concurrency rate is aligned with this value.
     * If there is currently less messages being processed than this value, more requests for messages will be made to meet this value. If there are
     * more messages being processed or requested than this value, this coordinating thread will block until enough messages have been processed.  If a
     * permit has not be obtained before the timeout defined by {@link #getConcurrencyPollingRate()}, it will recalculate this
     * concurrency rate again and wait for a permit.
     *
     * <p>Note that the concurrency rate does not get applied instantly and there are multiple attributing factors for when the rate of concurrency will
     * actually transition to a new concurrency rate when this value changes, for example:
     * <ul>
     *     <li>
     *         The rate of concurrency may take a while to decrease while messages being processed are finished. For example, if the concurrency rate was 10,
     *         10 messages will be requested and even though the concurrency rate may have decreased to 5, this concurrency rate
     *         will not decrease until 6 of those 10 threads have finished process a message and another can be requested.
     *     </li>
     *     <li>
     *         The delay in the concurrency rate change may be due to transitioning from allowing zero threads to a number of threads. In a worst
     *         case scenario there would be a delay of {@link #getConcurrencyPollingRate()} before the rate of concurrency is changed.
     *     </li>
     * </ul>
     *
     * @return the the level of concurrency for processing messages
     */
    @PositiveOrZero
    int getConcurrencyLevel();

    /**
     * The duration that the coordinating thread will sleep when the maximum rate of concurrency has been reached before checking the
     * concurrency rate again.
     *
     * <p>The reason that this property is needed is because during the processing of messages, which could be a significantly long period, the rate
     * of concurrency may have changed. The coordinating background thread should periodically check the concurrency rate while messages are processing
     * so that it can request more messages to be processed if that concurrency rate has increased. In the case that messages take a long time, there would
     * be a decrease in performance as it could have been processing more messages during this period.
     *
     * <p>There are performance considerations when determine an appropriate value for this property in that a higher polling period will result in less time
     * that the coordinating thread is awoken and is therefore less CPU intensive. However, decreasing this polling period makes it more responsive to changes
     * to the rate of concurrency.
     *
     * <p>If this duration is null or negative, {@link ConcurrentMessageBrokerConstants#DEFAULT_CONCURRENCY_POLLING} will be used instead. It is not
     * recommended to have a low value as that will result in this background thread constantly trying to determine if the concurrency rate can be
     * changed.
     *
     * @return the amount of time between polls for the concurrency level
     */
    @Nullable
    @Positive
    Duration getConcurrencyPollingRate();

    /**
     * The duration that the coordinating thread should backoff if there was an error trying to request a message.
     *
     * <p>This is needed to stop the background thread from trying again and again over and over causing a flood of error log messages that may make it
     * difficult to debug.
     *
     * <p>If this value is null or negative, {@link ConcurrentMessageBrokerConstants#DEFAULT_BACKOFF_TIME}.
     *
     * @return the amount of time to sleep the thread after an error is thrown
     */
    @Nullable
    @PositiveOrZero
    Duration getErrorBackoffTime();

    /**
     * The maximum number of message groups that can be cached before the {@link MessageBroker} should stop requesting more messages.
     *
     * <p>This can be used to allow for the the {@link MessageBroker} to prefetch more message groups than can be concurrently processed to improve
     * performance.
     *
     * @return the maximum number of message groups that can be cached
     */
    @Positive
    int getMaximumNumberOfCachedMessageGroups();

    /**
     * The function that will group the messages.
     *
     * <p>This grouping is what will make sure that two messages in the same group are not processed at the same time and done in order of retrieval. The
     * most common usage of this grouping function is using the {@link MessageSystemAttributeName#MESSAGE_GROUP_ID}.
     *
     * @return the message grouping function
     */
    Function<Message, String> messageGroupingFunction();

    /**
     * If there is a error processing a message in the group, determine whether all of the messages cached currently for that group should be removed.
     *
     * <p>This is useful for implementations like a FIFO message listener as a failing message should not allow the consumption of messages after that message
     * in the group.
     *
     * <p>Note that this won't work nicely with a {@link MessageRetriever} that batches its own messages internally, like the
     * {@link PrefetchingMessageRetriever} because the message is purged from this broker but not the message retriever.
     *
     * @return whether to purge extra messages in group on error
     */
    @Value.Default
    default boolean purgeExtraMessagesInGroupOnError() {
        return true;
    }

    /**
     * Determines whether during shutdown any internally cached messages should be processed.
     *
     * <p>Note that this will increase the amount of time that the container will take to shutdown as it must wait until all cached
     * messages have been placed onto a thread to process and the time for this is dependent on how long each message takes to process. Therefore
     * enabling this should only be done if processing a message does not take a long time or the maximum number of internally cached messages is
     * low.
     *
     * <p>This does not guarantee that some messages may slip through the cracks and not be processed. For example, there is a window where
     * a message is downloaded by the {@link MessageRetriever} but it isn't picked up by this broker and therefore will be lost. It is recommended
     * to have some sort of replay policy like a DLQ to handle these messages.
     *
     * @return whether any internally cached messages should be processed on shutdown
     */
    @Value.Default
    default boolean processCachedMessagesOnShutdown() {
        return false;
    }
}
