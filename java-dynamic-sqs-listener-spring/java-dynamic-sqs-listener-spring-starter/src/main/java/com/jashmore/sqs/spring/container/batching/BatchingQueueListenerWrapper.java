package com.jashmore.sqs.spring.container.batching;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.resolver.batching.StaticBatchingMessageResolverProperties;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.AbstractQueueAnnotationWrapper;
import com.jashmore.sqs.spring.IdentifiableMessageListenerContainer;
import com.jashmore.sqs.spring.QueueWrapper;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.spring.queue.QueueResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link QueueWrapper} that will wrap methods annotated with {@link QueueListener @QueueListener} with some predefined
 * implementations of the framework.
 */
@Slf4j
@RequiredArgsConstructor
public class BatchingQueueListenerWrapper extends AbstractQueueAnnotationWrapper<BatchingQueueListener> {
    private final ArgumentResolverService argumentResolverService;
    private final SqsAsyncClient sqsAsyncClient;
    private final QueueResolverService queueResolverService;

    @Override
    protected Class<BatchingQueueListener> getAnnotationClass() {
        return BatchingQueueListener.class;
    }

    @Override
    protected IdentifiableMessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method,
                                                                                  final BatchingQueueListener annotation) {
        final ExecutorService executor = Executors.newCachedThreadPool();
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(queueResolverService.resolveQueueUrl(annotation.value()))
                .build();

        final BatchingMessageRetrieverProperties batchingMessageRetrieverProperties = StaticBatchingMessageRetrieverProperties
                .builder()
                .visibilityTimeoutInSeconds(annotation.messageVisibilityTimeoutInSeconds())
                .messageRetrievalPollingPeriodInMs(annotation.maxPeriodBetweenBatchesInMs())
                .numberOfThreadsWaitingTrigger(annotation.concurrencyLevel())
                .build();
        final BatchingMessageRetriever messageRetriever = new BatchingMessageRetriever(
                queueProperties, sqsAsyncClient, executor, batchingMessageRetrieverProperties);

        final StaticBatchingMessageResolverProperties batchingMessageResolverProperties = StaticBatchingMessageResolverProperties.builder()
                .bufferingSizeLimit(annotation.concurrencyLevel())
                .bufferingTimeInMs(annotation.maxPeriodBetweenBatchesInMs())
                .build();
        final MessageResolver messageResolver = new BatchingMessageResolver(queueProperties, sqsAsyncClient, batchingMessageResolverProperties);

        final MessageProcessor messageProcessor = new DefaultMessageProcessor(argumentResolverService, queueProperties,
                messageResolver, method, bean);

        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                executor,
                StaticConcurrentMessageBrokerProperties
                        .builder()
                        .concurrencyLevel(annotation.concurrencyLevel())
                        .build()
        );

        final String identifier;
        if (StringUtils.isEmpty(annotation.identifier().trim())) {
            identifier = bean.getClass().getName() + "#" + method.getName();
        } else {
            identifier = annotation.identifier().trim();
        }

        return IdentifiableMessageListenerContainer.builder()
                .identifier(identifier)
                .container(new SimpleMessageListenerContainer(messageRetriever, messageBroker, messageResolver))
                .build();
    }
}
