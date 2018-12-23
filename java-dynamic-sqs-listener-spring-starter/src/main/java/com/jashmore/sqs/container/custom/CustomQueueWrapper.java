package com.jashmore.sqs.container.custom;

import com.jashmore.sqs.AbstractQueueAnnotationWrapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.QueueWrapper;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.container.basic.QueueListener;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.queue.QueueResolverService;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * Implementation of the {@link QueueWrapper} that constructs a {@link MessageListenerContainer} built by beans being supplied by a corresponding factory.
 *
 * <p>The usage for this would be for consumers that want to use their own {@link MessageRetriever}, {@link MessageProcessor} or {@link MessageBroker} that they
 * construct them self. For example, the {@link QueueListener} annotation is a helper annotation that will construct a {@link MessageListenerContainer} built
 * with a {@link ConcurrentMessageBroker}, {@link PrefetchingMessageRetriever} and {@link DefaultMessageProcessor} which may not be optimal for this listener.
 * Therefore the consumer can supply their own implementations via factory classes defined in the spring container.
 *
 * <p>An example usage for this could be:
 * <pre class="code">
 * &#064;Bean
 * public MessageRetrieverFactory myMessageRetrieverFactory(final AmazonSQSAsync amazonSQSAsync) {
 *     return (queueProperties) -> {
 *         final PrefetchingProperties prefetchingProperties = PrefetchingProperties
 *                 .builder()
 *                 .maxPrefetchedMessages(10)
 *                 .desiredMinPrefetchedMessages(0)
 *                 .maxWaitTimeInSecondsToObtainMessagesFromServer(10)
 *                 .visibilityTimeoutForMessagesInSeconds(30)
 *                 .errorBackoffTimeInMilliseconds(10)
 *                 .build();
 *         return new PrefetchingMessageRetriever(amazonSQSAsync, queueProperties, prefetchingProperties, Executors.newCachedThreadPool());
 *      };
 * }
 * </pre>
 *
 * @see CustomQueueListener for more information about each property that can be supplied for this wrapper
 */
@Service
@AllArgsConstructor
public class CustomQueueWrapper extends AbstractQueueAnnotationWrapper<CustomQueueListener> {
    private final BeanFactory beanFactory;
    private final QueueResolverService queueResolverService;

    @Override
    public Class<CustomQueueListener> getAnnotationClass() {
        return CustomQueueListener.class;
    }

    @Override
    public MessageListenerContainer wrapMethodContainingAnnotation(final Object bean,
                                                                   final Method method,
                                                                   final CustomQueueListener annotation) {
        final String queueNameOrUrl = annotation.queue();

        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(queueResolverService.resolveQueueUrl(queueNameOrUrl))
                .build();

        final MessageRetrieverFactory messageRetrieverFactory = beanFactory.getBean(
                annotation.messageRetrieverFactoryBeanName(), MessageRetrieverFactory.class);

        final MessageRetriever messageRetriever = messageRetrieverFactory.createMessageRetriever(queueProperties);

        final MessageProcessorFactory messageProcessorFactory = beanFactory.getBean(
                annotation.messageProcessorFactoryBeanName(), MessageProcessorFactory.class);

        final MessageProcessor messageProcessor = messageProcessorFactory.createMessageProcessor(queueProperties, bean, method);

        final MessageBrokerFactory messageBrokerFactory = beanFactory.getBean(
                annotation.messageBrokerFactoryBeanName(), MessageBrokerFactory.class);

        final MessageBroker messageBroker = messageBrokerFactory.createMessageBroker(messageRetriever, messageProcessor);

        final String identifier;
        if (StringUtils.isEmpty(annotation.identifier().trim())) {
            identifier = bean.getClass().getName() + "#" + method.getName();
        } else {
            identifier = annotation.identifier().trim();
        }

        if (messageRetriever instanceof AsyncMessageRetriever) {
            return new SimpleMessageListenerContainer(identifier, (AsyncMessageRetriever)messageRetriever, messageBroker);
        } else {
            return new SimpleMessageListenerContainer(identifier, messageBroker);
        }
    }
}
