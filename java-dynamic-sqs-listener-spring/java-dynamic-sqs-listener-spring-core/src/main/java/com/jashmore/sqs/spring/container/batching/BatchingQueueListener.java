package com.jashmore.sqs.spring.container.batching;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import org.springframework.core.env.Environment;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Wrap a method with a {@link MessageListenerContainer} that will execute the method whenever a message is received on the provided queue. The goal of this
 * listener compared to others, like the {@link QueueListener @QueueListener} is to reduce the amount of requests
 * that are made to the SQS Server, e.g. by batching requests for messages and requests to delete the messages from the queue.
 *
 * <p>This is a simplified annotation that uses the {@link ConcurrentMessageBroker}, {@link BatchingMessageRetriever} and a {@link DefaultMessageProcessor}
 * with a {@link BatchingMessageResolver} as the components of the library. Not all of the properties for each implementation are available to simplify
 * this usage.
 *
 * @see BatchingQueueListenerWrapper for what processes this annotation
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface BatchingQueueListener {
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
     * @see Environment#resolveRequiredPlaceholders(String) for how the placeholders are resolved
     * @see QueueProperties#getQueueUrl() for how the URL of the queue is resolved if a queue name is supplied here
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
     * The total number of threads requesting messages that will result in the the background thread to actually request the messages.
     *
     * <p>This number should be positive but smaller than {@link AwsConstants#MAX_NUMBER_OF_MESSAGES_FROM_SQS} as it does not make sense to have a batch size
     * greater than what AWS can provide.
     *
     * @return the total number of threads requesting messages for trigger a batch of messages to be retrieved
     * @see BatchingMessageRetrieverProperties#getNumberOfThreadsWaitingTrigger() for more details about this parameter
     */
    int batchSize() default 5;

    /**
     * The total number of threads requesting messages that will result in the the background thread to actually request the messages.
     *
     * <p>This number should be positive but smaller than {@link AwsConstants#MAX_NUMBER_OF_MESSAGES_FROM_SQS} as it does not make sense to have a batch size
     * greater than what AWS can provide.
     *
     * <p>This can be used when you need to load the value from Spring properties for example
     * <pre>batchSizeString = "${my.profile.property}"</pre> instead of having it hardcoded in {@link #batchSize()}.
     *
     * @return the total number of threads requesting messages for trigger a batch of messages to be retrieved
     * @see BatchingMessageRetrieverProperties#getNumberOfThreadsWaitingTrigger() for more details about this parameter
     */
    String batchSizeString() default "";

    /**
     * The maximum period of time that the {@link BatchingMessageRetriever} will wait for all threads to be ready before retrieving messages.
     *
     * @return the period in ms that threads will wait for messages to be requested from SQS
     * @see BatchingMessageRetrieverProperties#getMessageRetrievalPollingPeriodInMs() for more details about this parameter
     */
    long maxPeriodBetweenBatchesInMs() default 2000L;

    /**
     * The maximum period of time that the {@link BatchingMessageRetriever} will wait for all threads to be ready before retrieving messages converted
     * from a string representation.
     *
     * <p>This can be used when you need to load the value from Spring properties for example
     * <pre>maxPeriodBetweenBatchesInMsString = "${my.profile.property}"</pre> instead of having it hardcoded in {@link #maxPeriodBetweenBatchesInMs()}.
     *
     * @return the period in ms that threads will wait for messages to be requested from SQS
     * @see BatchingMessageRetrieverProperties#getMessageRetrievalPollingPeriodInMs() for more details
     * @see #maxPeriodBetweenBatchesInMs() for more information about this field
     */
    String maxPeriodBetweenBatchesInMsString() default "";

    /**
     * The message visibility that will be used for messages obtained from the queue.
     *
     * @return the message visibility for messages fetched from the queue
     * @see BatchingMessageRetrieverProperties#getVisibilityTimeoutInSeconds() for more details and constraints
     */
    int messageVisibilityTimeoutInSeconds() default 30;

    /**
     * The message visibility that will be used for messages obtained from the queue converted from a string representation.
     *
     * <p>This can be used when you need to load the value from Spring properties for example
     * <pre>messageVisibilityTimeoutInSeconds = "${my.profile.property}"</pre> instead of having it hardcoded in {@link #messageVisibilityTimeoutInSeconds()}.
     *
     * @return the message visibility for messages fetched from the queue
     * @see BatchingMessageRetrieverProperties#getVisibilityTimeoutInSeconds() for more details and constraints
     */
    String messageVisibilityTimeoutInSecondsString() default "";
}
