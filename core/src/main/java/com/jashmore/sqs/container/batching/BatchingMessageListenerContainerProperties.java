package com.jashmore.sqs.container.batching;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainerProperties;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import java.time.Duration;
import org.immutables.value.Value;

@Value.Immutable
public interface BatchingMessageListenerContainerProperties {
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
     * @see BatchingMessageRetrieverProperties#getErrorBackoffTime() for more details
     * @see ConcurrentMessageBrokerProperties#getErrorBackoffTime() for more details
     */
    @Nullable
    Duration errorBackoffTime();

    /**
     * The total number of threads requesting messages that will result in the the background thread to actually request the messages.
     *
     * <p>This number should be positive but smaller than {@link AwsConstants#MAX_NUMBER_OF_MESSAGES_FROM_SQS} as it does not make sense to have a batch size
     * greater than what AWS can provide.
     *
     * @return the total number of threads requesting messages for trigger a batch of messages to be retrieved
     * @see BatchingMessageRetrieverProperties#getBatchSize() for more details about this parameter
     */
    @Positive
    @Max(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS)
    int batchSize();

    /**
     * The maximum period of time that the background thread will wait for the number of threads waiting for messages to reach
     * {@link #batchSize()} before requesting messages regardless of this count.
     *
     * <p>Note that the background thread threads will ignore this period if the current number of threads requesting messages goes over
     * the {@link #batchSize()} limit.
     *
     * <p>This value must be greater than zero as it does not make sense for it to be negative. It is also recommended not to have this as a
     * very small time duration as a small duration will result in a constant looping of the buffering thread.
     *
     * @return the polling period between attempts to get messages
     * @see BatchingMessageRetrieverProperties#getBatchingPeriod() for more details about this parameter
     */
    @Nullable
    @Positive
    Duration getBatchingPeriod();

    /**
     * The message visibility that will be used for messages obtained from the queue.
     *
     * @return the message visibility for messages fetched from the queue
     * @see BatchingMessageRetrieverProperties#getMessageVisibilityTimeout() for more details and constraints
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
    @Value.Default
    default boolean processAnyExtraRetrievedMessagesOnShutdown() {
        return true;
    }

    /**
     * Determines whether the threads that are processing messages should be interrupted during shutdown.
     *
     * @return whether to interrupt message processing threads on shutdown
     * @see CoreMessageListenerContainerProperties#shouldInterruptThreadsProcessingMessagesOnShutdown() for more details
     */
    @Value.Default
    default boolean interruptThreadsProcessingMessagesOnShutdown() {
        return false;
    }
}
