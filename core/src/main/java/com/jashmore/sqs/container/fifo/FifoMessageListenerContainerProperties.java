package com.jashmore.sqs.container.fifo;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.grouping.GroupingMessageBrokerProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import java.time.Duration;
import org.immutables.value.Value;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Properties for configuration a {@link FifoMessageListenerContainer}.
 */
@Value.Immutable
public interface FifoMessageListenerContainerProperties {
    /**
     * The unique identifier for this listener.
     *
     * <p>This can be used if you need to access the {@link MessageListenerContainer} for this queue listener specifically to start/stop it
     * specifically.
     *
     * <p>The identifier for the queue will also be used to name the threads that will be executing the message processing. For example if your identifier
     * is <pre>'my-queue-method'</pre> the threads that will be created will be named like <pre>'my-queue-method-0'</pre>, etc.
     *
     * @return the unique identifier for this queue listener
     */
    String identifier();

    /**
     * The number of threads that will be processing messages.
     *
     * @return the total number of threads processing messages
     * @see GroupingMessageBrokerProperties#getConcurrencyLevel() for more details and constraints
     */
    int concurrencyLevel();

    /**
     * The recommended amount of time to wait to change the rate of concurrency.
     *
     * <p>If this is null, a default value will be used.
     *
     * @return the recommended amount of time to wait for a change in concurrency
     * @see GroupingMessageBrokerProperties#getConcurrencyPollingRate() for more details
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
     * @see GroupingMessageBrokerProperties#getErrorBackoffTime()  for more details
     */
    @Nullable
    Duration errorBackoffTime();

    /**
     * The maximum number of messages that can be downloaded and processed for a single {@link MessageSystemAttributeName#MESSAGE_GROUP_ID}.
     *
     * <p>When a consumer downloads messages from a group, all others consumers (including this one) will not be able to obtain any more messages from that
     * message group. This will therefore define the maximum number of messages that can be obtained from SQS in a single call to AWS.
     *
     * @return the maximum number of messages in a single message group
     * @see ReceiveMessageRequest#maxNumberOfMessages() for more details about the AWS SDK
     * @see BatchingMessageRetrieverProperties#getBatchSize() for more details about the retriever that will consume this value
     */
    int maximumMessagesInMessageGroup();

    /**
     * The maximum number of messages in distinct message that be cached before requests for more messages will be stopped.
     *
     * <p>This can be useful when there are a large number of message groups and it is desirable to prefetch messages to increase throughput. Note that caution
     * should be taken with making sure that the visibility timeout for messages are not less than the time to process messages otherwise the messages that
     * are cached may be placed back onto the queue if there is a replay policy.
     *
     * @return the maximum number of messages groups to cache
     * @see GroupingMessageBrokerProperties#getMaximumNumberOfCachedMessageGroups()
     */
    int maximumCachedMessageGroups();

    /**
     * The amount of time that a message should be invisible from other consumers.
     *
     * <p>If this is null, the default visibility timeout configured in the SQS will be used.
     *
     * @return the amount of time that a message
     * @see ReceiveMessageRequest#visibilityTimeout() for more details
     * @see BatchingMessageRetrieverProperties#getMessageVisibilityTimeout() for more details
     */
    @Nullable
    Duration messageVisibilityTimeout();

    /**
     * Whether to try and process any internally cached messages when the container is being shutdown.
     *
     * <p>This cannot guarantee that all messages will be processed as there can be little race conditions with getting messages from the
     * {@link MessageRetriever} to the {@link MessageBroker} but it will try and process as many as possible.
     *
     * @return whether to process as many cached messages as possible
     */
    @Value.Default
    default boolean tryAndProcessAnyExtraRetrievedMessagesOnShutdown() {
        return false;
    }

    /**
     * Whether to interrupt currently processing messages when the container is being shutdown.
     *
     * @return whether to interrupt threads during shutdown
     */
    @Value.Default
    default boolean interruptThreadsProcessingMessagesOnShutdown() {
        return false;
    }
}
