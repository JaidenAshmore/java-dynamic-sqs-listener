package com.jashmore.sqs.spring.container.custom;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.spring.container.MessageListenerContainer;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import org.springframework.core.env.Environment;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Wrap a method with a {@link MessageListenerContainer} that will execute the method whenever a message is received on the provided queue using custom provided
 * components of this framework.
 *
 * <p>This is a custom annotation that allows for each component of the framework to be provided by a corresponding factory and therefore any type of
 * implementation can be used.
 *
 * @see CustomQueueWrapper for what processes this annotation
 * @see QueueListener for a more simplified version that uses
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface CustomQueueListener {
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
    String queue();

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
     * The name of the bean for the {@link MessageRetrieverFactory} that should be used for this queue listener.
     *
     * <p>In the following example the bean name in this field would be "myMessageRetrieverFactory" and the definition of this bean that will create
     * a {@link PrefetchingMessageRetriever} is something like:
     *
     * <pre class="code">
     * &#064;Bean
     * public MessageRetrieverFactory myMessageRetrieverFactory(final SqsAsyncClient amazonSQSAsync) {
     *     return (queueProperties) -&gt; {
     *         final PrefetchingProperties prefetchingProperties = PrefetchingProperties
     *                 .builder()
     *                 .maxPrefetchedMessages(10)
     *                 .desiredMinPrefetchedMessages(1)
     *                 .maxWaitTimeInSecondsToObtainMessagesFromServer(10)
     *                 .visibilityTimeoutForMessagesInSeconds(30)
     *                 .errorBackoffTimeInMilliseconds(10)
     *                 .build();
     *         return new PrefetchingMessageRetriever(amazonSQSAsync, queueProperties, prefetchingProperties, Executors.newCachedThreadPool());
     *      };
     * }
     * </pre>
     *
     * @return the name of the {@link MessageRetrieverFactory} bean
     */
    String messageRetrieverFactoryBeanName();

    /**
     * The name of the bean for the {@link MessageProcessorFactory} that should be used for this queue listener.
     *
     * <p>In the following example the bean name in this field would be "myMessageProcessorFactory" and the definition of this bean that will create
     * a {@link DefaultMessageProcessor} is something like:
     *
     * <pre class="code">
     * &#064;Bean
     * public MessageProcessorFactory myMessageProcessorFactory(final ArgumentResolverService argumentResolverService,
     *                                                          final SqsAsyncClient amazonSQSAsync) {
     *     return (queueProperties, bean, method) -&gt; new DefaultMessageProcessor(argumentResolverService, queueProperties, amazonSQSAsync, method, bean);
     * }
     * </pre>
     *
     * @return the name of the {@link MessageProcessorFactory} bean
     */
    String messageProcessorFactoryBeanName();


    /**
     * The name of the bean for the {@link MessageBrokerFactory} that should be used for this queue listener.
     *
     * <p>In the following example the bean name in this field would be "myMessageBrokerFactory" and the definition of this bean that will create
     * a {@link ConcurrentMessageBroker} is something like:
     *
     * <pre class="code">
     * &#064;Bean
     * public MessageBrokerFactory myMessageBrokerFactory() {
     *     return (messageRetriever, messageProcessor) -&gt; {
     *             final ConcurrentMessageBrokerProperties properties = StaticConcurrentMessageBrokerProperties
     *                       .builder()
     *                       .concurrencyLevel(2)
     *                       .preferredConcurrencyPollingRateInMilliseconds(1000)
     *                       .build();
     *             return new ConcurrentMessageBroker(messageRetriever, messageProcessor, Executors.newCachedThreadPool(), properties);
     *    };
     * }
     * </pre>
     *
     * @return the name of the {@link MessageBrokerFactory} bean
     */
    String messageBrokerFactoryBeanName();
}
