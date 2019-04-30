package com.jashmore.sqs.spring.container.prefetch;

import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import org.springframework.core.env.Environment;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Wrap a method with a {@link MessageListenerContainer} that will execute the method whenever a message is received on the provided queue.
 *
 * <p>This is a simplified annotation that uses the {@link ConcurrentMessageBroker}, {@link PrefetchingMessageRetriever} and {@link DefaultMessageProcessor}
 * for the implementations of the framework. Not all of the properties for each implementation are available to simplify this usage.
 *
 * @see PrefetchingQueueListenerWrapper for what processes this annotation
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
     * @see Environment#resolveRequiredPlaceholders(String) for how the placeholders are resolved
     * @see QueueProperties#queueUrl for how the URL of the queue is resolved if a queue name is supplied here
     */
    String value();

    /**
     * The unique identifier for this listener.
     *
     * <p>This can be used if you need to access the {@link MessageListenerContainer} for this queue listener specifically to start/stop it
     * specifically.
     *
     * <p>If no value is provided for the identifier the class path and method name is used as the unique identifier. For example,
     * <pre>com.company.queues.MyQueue#method(String, String)</pre>.
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
     * The minimum number of messages that are should be prefetched before it tries to fetch more messages.
     *
     * @return the minimum number of prefetched messages
     * @see StaticPrefetchingMessageRetrieverProperties#desiredMinPrefetchedMessages for more details and constraints
     */
    int desiredMinPrefetchedMessages() default 1;

    /**
     * The total number of messages that can be prefetched from the server and stored in memory for execution.
     *
     * @return the max number of prefetched issues
     * @see StaticPrefetchingMessageRetrieverProperties#maxPrefetchedMessages for more details and constraints
     */
    int maxPrefetchedMessages() default MAX_NUMBER_OF_MESSAGES_FROM_SQS;

    /**
     * The message visibility that will be used for messages obtained from the queue.
     *
     * @return the message visibility for messages fetched from the queue
     * @see StaticPrefetchingMessageRetrieverProperties#visibilityTimeoutForMessagesInSeconds for more details and constraints
     */
    int messageVisibilityTimeoutInSeconds() default 30;
}
