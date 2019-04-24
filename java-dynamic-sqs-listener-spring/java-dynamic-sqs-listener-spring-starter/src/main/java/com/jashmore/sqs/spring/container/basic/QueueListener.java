package com.jashmore.sqs.spring.container.basic;


import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import org.springframework.core.env.Environment;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Wrap a method with a {@link MessageListenerContainer} that will execute the method whenever a message is received on the provided queue.
 *
 * <p>This is a simplified annotation that uses the {@link ConcurrentMessageBroker}, {@link BatchingMessageRetriever} and {@link DefaultMessageProcessor}
 * for the implementations of the framework. Not all of the properties for each implementation are available to simplify this usage.
 *
 * @see QueueListenerWrapper for what processes this annotation
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface QueueListener {
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
     * The maximum period of time that the {@link BatchingMessageRetriever} will wait for all threads to be ready before retrieving messages.
     *
     * <p>This tries to reduce the number of times that requests for messages are made to SQS by waiting for all of the threads to be requiring messages
     * before requesting for messages from SQS. If one or more of the threads processing messages does not start requesting messages by this period's
     * timeout the current threads waiting for messages will have messages requested for them
     *
     * @return the period in ms that threads will wait for messages to be requested from SQS
     * @see BatchingMessageRetrieverProperties#getMessageRetrievalPollingPeriodInMs() for more details
     */
    long maxPeriodBetweenBatchesInMs() default 2000L;

    /**
     * The message visibility that will be used for messages obtained from the queue.
     *
     * @return the message visibility for messages fetched from the queue
     * @see BatchingMessageRetrieverProperties#getVisibilityTimeoutInSeconds() for more details and constraints
     */
    int messageVisibilityTimeoutInSeconds() default 30;
}
