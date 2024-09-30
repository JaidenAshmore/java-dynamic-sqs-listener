package com.jashmore.sqs.micronaut.container.prefetch;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.micronaut.client.SqsAsyncClientProvider;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Wrap a method with a {@link MessageListenerContainer} that will execute the method whenever a message is received on the provided queue.
 *
 * <p>This is a simplified annotation that uses the {@link ConcurrentMessageBroker}, {@link PrefetchingMessageRetriever} and {@link CoreMessageProcessor}
 * for the implementations of the framework. Not all of the properties for each implementation are available to simplify this usage.
 *
 * @see PrefetchingMessageListenerContainerFactory for what processes this annotation
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface PrefetchingQueueListener {
    /**
     * The queue name or url for the queue to listen to messages on, this may contain placeholders that can be resolved from the Spring Environment.
     *
     * <p>Examples of this field can be:
     * <ul>
     *     <li>"${my.queue.prop}" which would be resolved to "http://localhost:4576/q/myQueue" if the application.yml contains
     *         my.queue.prop=http://localhost:4576/q/myQueue</li>
     *     <li>"http://localhost:4576/q/myQueue" which will be used as is</li>
     *     <li>"myQueue" which could be resolved to something like "http://localhost:4576/q/myQueue" by getting the URL from SQS</li>
     * </ul>
     *
     * @return the queue name or URL of the queue
     * @see io.micronaut.context.env.PropertyPlaceholderResolver#resolveRequiredPlaceholders(String) for how the placeholders are resolved
     * @see QueueProperties#getQueueUrl()  for how the URL of the queue is resolved if a queue name is supplied here
     */
    String value();

    /**
     * The unique identifier for this listener.
     *
     * <p>This can be used if you need to access the {@link MessageListenerContainer} for this queue listener specifically to start/stop it
     * specifically.
     *
     * <p>If no value is provided for the identifier the class path and method name is used as the unique identifier. For example, the method
     * <pre>com.company.queues.MyQueue#method(String, String)</pre> would result in the following identifier <pre>my-queue-method</pre>.
     *
     * <p>The identifier for the queue will also be used to name the threads that will be executing the message processing. For example if your identifier
     * is <pre>'my-queue-method'</pre> the threads that will be created will be named like <pre>'my-queue-method-0'</pre>, etc.
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
     * @return the total number of threads processing messages
     * @see ConcurrentMessageBrokerProperties#getConcurrencyLevel() for more details and constraints
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
     * @see ConcurrentMessageBrokerProperties#getConcurrencyLevel() for more details and constraints
     */
    String concurrencyLevelString() default "";

    /**
     * The minimum number of messages that are should be prefetched before it tries to fetch more messages.
     *
     * @return the minimum number of prefetched messages
     * @see PrefetchingMessageRetrieverProperties#getDesiredMinPrefetchedMessages() for more details and constraints
     */
    int desiredMinPrefetchedMessages() default 1;

    /**
     * The minimum number of messages that are should be prefetched before it tries to fetch more messages.
     *
     * <p>This can be used when you need to load the value from Spring properties for example
     * <pre>desiredMinPrefetchedMessagesString = "${my.profile.property}"</pre> instead of having it hardcoded in {@link #desiredMinPrefetchedMessages()}.
     *
     * <p>If this value is not empty, the value set by {@link #desiredMinPrefetchedMessages()} will be ignored.
     *
     * @return the minimum number of prefetched messages
     * @see PrefetchingMessageRetrieverProperties#getDesiredMinPrefetchedMessages() for more details and constraints
     */
    String desiredMinPrefetchedMessagesString() default "";

    /**
     * The total number of messages that can be prefetched from the server and stored in memory for execution.
     *
     * @return the max number of prefetched issues
     * @see PrefetchingMessageRetrieverProperties#getMaxPrefetchedMessages()  for more details and constraints
     */
    int maxPrefetchedMessages() default MAX_NUMBER_OF_MESSAGES_FROM_SQS;

    /**
     * The total number of messages that can be prefetched from the server and stored in memory for execution built from a string representation.
     *
     * <p>This can be used when you need to load the value from Spring properties for example <pre>maxPrefetchedMessages = "${my.profile.property}"</pre>
     * instead of having it hardcoded in {@link #maxPrefetchedMessages()}.
     *
     * <p>If this value is not empty, the value set by {@link #maxPrefetchedMessages()} will be ignored.
     *
     * @return the max number of prefetched issues
     * @see PrefetchingMessageRetrieverProperties#getMaxPrefetchedMessages()  for more details and constraints
     */
    String maxPrefetchedMessagesString() default "";

    /**
     * The message visibility that will be used for messages obtained from the queue.
     *
     * @return the message visibility for messages fetched from the queue
     * @see PrefetchingMessageRetrieverProperties#getMessageVisibilityTimeout() for more details and constraints
     */
    int messageVisibilityTimeoutInSeconds() default -1;

    /**
     * The message visibility that will be used for messages obtained from the queue converted from a string representation.
     *
     * <p>This can be used when you need to load the value from Spring properties for example
     * <pre>messageVisibilityTimeoutInSeconds = "${my.profile.property}"</pre> instead of having it hardcoded in {@link #messageVisibilityTimeoutInSeconds()}.
     *
     * @return the message visibility for messages fetched from the queue
     * @see BatchingMessageRetrieverProperties#getMessageVisibilityTimeout() for more details and constraints
     */
    String messageVisibilityTimeoutInSecondsString() default "";

    /**
     * Determines whether any extra messages that may have been downloaded but not yet processed should be processed before shutting down the container.
     *
     * <p>The shutdown time for the container will be dependent on the time it takes to process these extra messages.
     *
     * @return if any extra messages should be processed on shutdown
     */
    boolean processAnyExtraRetrievedMessagesOnShutdown() default true;

    /**
     * Determines whether the threads that are processing messages should be interrupted during shutdown.
     *
     * @return whether to interrupt message processing threads on shutdown
     */
    boolean interruptThreadsProcessingMessagesOnShutdown() default false;
}
