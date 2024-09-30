package com.jashmore.sqs.micronaut.container.fifo;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.broker.grouping.GroupingMessageBroker;
import com.jashmore.sqs.broker.grouping.GroupingMessageBrokerProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainerProperties;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import com.jashmore.sqs.micronaut.client.SqsAsyncClientProvider;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Wrap a method with a {@link MessageListenerContainer} that will listen to a FIFO SQS queue.
 *
 * @see FifoMessageListenerContainerFactory for what processes this annotation
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface FifoQueueListener {
    /**
     * The queue name or url for the queue to listen to messages on.
     *
     * <p>This may contain placeholders that can be resolved from the Spring Environment and must end in `.fifo` as this is a requirement on FIFO queue names.
     *
     * <p>Examples of this field can be:
     * <ul>
     *     <li>"${my.queue.prop}" which would be resolved to "http://localhost:4576/q/myQueue.fifo" if the application.yml contains
     *         my.queue.prop=http://localhost:4576/q/myQueue.fifo</li>
     *     <li>"http://localhost:4576/q/myQueue.fifo" which will be used as is</li>
     *     <li>"myQueue" which could be resolved to something like "http://localhost:4576/q/myQueue.fifo" by getting the Queue URL from SQS</li>
     * </ul>
     *
     * @return the queue name or URL of the queue
     * @see io.micronaut.context.env.PropertyPlaceholderResolver#resolveRequiredPlaceholders(String) for how the placeholders are resolved
     * @see QueueProperties#getQueueUrl() for how the URL of the queue is resolved if a queue name is supplied here
     */
    String value();

    /**
     * The unique identifier for this listener.
     *
     * <p>This can be used if you need to access the {@link MessageListenerContainer} for this message listener specifically to start or stop it.
     *
     * <p>If no value is provided for the identifier the class path and method name is used as the unique identifier. For example, the method
     * <pre>com.company.queues.MyQueue#method(String, String)</pre> would result in the following identifier <pre>my-queue-method</pre>.
     *
     * <p>The identifier for the queue will also be used to name the threads that will be executing the message processing. For example if your identifier
     * is <pre>'my-queue-method'</pre> the threads that will be created will be named like <pre>'my-queue-method-message-broker'</pre>, etc.
     *
     * @return the unique identifier for this queue listener
     */
    String identifier() default "";

    /**
     * The unique identifier for the {@link SqsAsyncClient} that should be used for this queue.
     *
     * <p>As queues can be set up across multiple AWS Accounts there can be multiple {@link SqsAsyncClient}s being
     * provided by the {@link SqsAsyncClientProvider}. When this identifier is set, it will obtain the client to be used
     * via the {@link SqsAsyncClientProvider#getClient(String)} method.
     *
     * <p>If this value is not set (empty), the default client will be provided by a call to {@link SqsAsyncClientProvider#getDefaultClient()}.
     *
     * @return the identifier for the client to use or empty if the default should be used
     */
    String sqsClient() default "";

    /**
     * The number of threads that will be processing messages.
     *
     * <p>This value is ignored when {@link #concurrencyLevelString()} has been set and is not an empty string.
     *
     * @return the total number of threads processing messages
     * @see FifoMessageListenerContainerProperties#concurrencyLevel() for more details
     */
    int concurrencyLevel() default 5;

    /**
     * The number of threads that will be processing messages converted from a string representation.
     *
     * <p>This can be used when you need to load the value from Spring properties for example <pre>concurrencyLevelString = "${my.profile.property}"</pre>
     * instead of having it hardcoded in {@link #concurrencyLevel()}.
     *
     * <p>If this value is not empty, the value set by {@link #concurrencyLevel()} will be ignored.
     *
     * @return the total number of threads processing messages as a string
     * @see FifoMessageListenerContainerProperties#concurrencyLevel() for more details
     */
    String concurrencyLevelString() default "";

    /**
     * The maximum number of messages that can be downloaded for a single message group at once.
     *
     * <p>This number should be positive but smaller than {@link AwsConstants#MAX_NUMBER_OF_MESSAGES_FROM_SQS} as SQS restricts the amount of messages that
     * can be downloaded at once to that number.
     *
     * <p>This value is ignored when {@link #maximumMessagesInMessageGroupString()} has been set and is not an empty string.
     *
     * @return the total number of messages that should be requested at once
     * @see FifoMessageListenerContainerProperties#maximumMessagesInMessageGroup() for more details
     */
    int maximumMessagesInMessageGroup() default 2;

    /**
     * The maximum number of messages that can be downloaded for a single message group at once.
     *
     * <p>This number should be positive but smaller than {@link AwsConstants#MAX_NUMBER_OF_MESSAGES_FROM_SQS} as SQS restricts the amount of messages that
     * can be downloaded at once to that number.
     *
     * <p>This can be used when you need to load the value from Spring properties for example
     * <pre>maximumMessagesInMessageGroupString = "${my.profile.property}"</pre> instead of having it hardcoded in {@link #maximumMessagesInMessageGroup()}.
     *
     * @return the total number of threads requesting messages for trigger a batch of messages to be retrieved
     * @see FifoMessageListenerContainerProperties#maximumMessagesInMessageGroup() for more details
     */
    String maximumMessagesInMessageGroupString() default "";

    /**
     * The maximum number of distinct message groups that can be cached internally before it will stop requesting more messages.
     *
     * <p>Configuring this at a higher level than the {@link #concurrencyLevel()} will act like it is prefetching messages for faster processing.
     *
     * <p>This value is ignored when {@link #maximumCachedMessageGroupsString()} has been set and is not an empty string.
     *
     * @return the maximum number of distinct cached message groups
     * @see GroupingMessageBrokerProperties#getMaximumNumberOfCachedMessageGroups() for more details
     */
    int maximumCachedMessageGroups() default 15;

    /**
     * The maximum number of distinct message groups that can be cached internally before it will stop requesting more messages from a string representation.
     *
     * <p>Configuring this at a higher level than the {@link #concurrencyLevel()} will act like it is prefetching messages for faster processing.
     *
     * <p>This can be used when you need to load the value from Spring properties for example
     * <pre>maximumCachedMessageGroupsString = "${my.profile.property}"</pre> instead of having it hardcoded in {@link #messageVisibilityTimeoutInSeconds()}.
     *
     * @return the maximum number of distinct cached message groups
     * @see GroupingMessageBrokerProperties#getMaximumNumberOfCachedMessageGroups() for more details
     */
    String maximumCachedMessageGroupsString() default "";

    /**
     * The message visibility that will be used for messages obtained from the queue.
     *
     * <p>If this value is not set, the default visibility timeout from the SQS queue will be used.
     *
     * <p>This value is ignored when {@link #messageVisibilityTimeoutInSecondsString()} has been set and is not an empty string.
     *
     * @return the message visibility for messages fetched from the queue
     * @see BatchingMessageRetrieverProperties#getMessageVisibilityTimeout() for more details and constraints
     */
    int messageVisibilityTimeoutInSeconds() default -1;

    /**
     * The message visibility that will be used for messages obtained from the queue converted from a string representation.
     *
     * <p>This can be used when you need to load the value from Spring properties for example
     * <pre>messageVisibilityTimeoutInSecondsString = "${my.profile.property}"</pre> instead of having it hardcoded
     * in {@link #messageVisibilityTimeoutInSeconds()}.
     *
     * @return the message visibility for messages fetched from the queue
     * @see BatchingMessageRetrieverProperties#getMessageVisibilityTimeout() for more details and constraints
     */
    String messageVisibilityTimeoutInSecondsString() default "";

    /**
     * Determines whether any extra messages that may have been internally cached in the {@link GroupingMessageBroker} should be processed during the
     * shutdown.
     *
     * <p>The shutdown time for the container will be dependent on the time it takes to process these extra messages.
     *
     * @return if any extra messages should be processed on shutdown
     */
    boolean tryAndProcessAnyExtraRetrievedMessagesOnShutdown() default false;

    /**
     * Determines whether the threads that are processing messages should be interrupted during shutdown.
     *
     * @return whether to interrupt message processing threads on shutdown
     */
    boolean interruptThreadsProcessingMessagesOnShutdown() default false;
}
