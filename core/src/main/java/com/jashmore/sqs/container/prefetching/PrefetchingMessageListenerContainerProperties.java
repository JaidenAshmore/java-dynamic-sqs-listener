package com.jashmore.sqs.container.prefetching;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainerProperties;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties;
import java.time.Duration;
import org.immutables.value.Value;

/**
 * Properties for configuration a {@link PrefetchingMessageListenerContainer}.
 */
@Value.Immutable
public interface PrefetchingMessageListenerContainerProperties {
    /**
     * The number of threads that will be processing messages.
     *
     * @return the total number of threads processing messages
     * @see ConcurrentMessageBrokerProperties#getConcurrencyLevel() for more details and constraints
     */
    @PositiveOrZero
    int concurrencyLevel();

    /**
     * The recommended amount of time to wait to change the rate of concurrency.
     *
     * <p>If this is null, a default value will be used.
     *
     * @return the recommended amount of time to wait for a change in concurrency
     * @see ConcurrentMessageBrokerProperties#getConcurrencyPollingRate() for more details and constraints
     */
    @Nullable
    Duration concurrencyPollingRate();

    /**
     * The amount of time to backoff if there was an exception obtaining a message to process.
     *
     * <p>This prevents a constant spinning of errors if there is a persistent error obtaining messages.
     *
     * <p>If this is null, a default value will be used
     *
     * @return the backoff time if there is an error requesting messages
     * @see PrefetchingMessageRetrieverProperties#getErrorBackoffTime() for more details
     * @see ConcurrentMessageBrokerProperties#getErrorBackoffTime() for more details
     */
    @Nullable
    Duration errorBackoffTime();

    /**
     * The minimum number of messages that are should be prefetched before it tries to fetch more messages.
     *
     * @return the minimum number of prefetched messages
     * @see PrefetchingMessageRetrieverProperties#getDesiredMinPrefetchedMessages() for more details and constraints
     */
    @Positive
    int desiredMinPrefetchedMessages();

    /**
     * The total number of messages that can be prefetched from the server and stored in memory for execution.
     *
     * @return the max number of prefetched issues
     * @see PrefetchingMessageRetrieverProperties#getMaxPrefetchedMessages()  for more details and constraints
     */
    @Positive
    int maxPrefetchedMessages();

    /**
     * The message visibility that will be used for messages obtained from the queue.
     *
     * @return the message visibility for messages fetched from the queue
     * @see PrefetchingMessageRetrieverProperties#getMessageVisibilityTimeout() for more details and constraints
     */
    @Nullable
    @Positive
    Duration messageVisibilityTimeout();

    /**
     * Determines whether any extra messages that may have been downloaded but not yet processed should be processed before shutting down the container.
     *
     * <p>The shutdown time for the container will be dependent on the time it takes to process these extra messages.
     *
     * @return if any extra messages should be processed on shutdown
     * @see CoreMessageListenerContainerProperties#shouldProcessAnyExtraRetrievedMessagesOnShutdown() for more details
     */
    default boolean processAnyExtraRetrievedMessagesOnShutdown() {
        return true;
    }

    /**
     * Determines whether the threads that are processing messages should be interrupted during shutdown.
     *
     * @return whether to interrupt message processing threads on shutdown
     * @see CoreMessageListenerContainerProperties#shouldInterruptThreadsProcessingMessagesOnShutdown() for more details
     */
    default boolean interruptThreadsProcessingMessagesOnShutdown() {
        return false;
    }
}
