package com.jashmore.sqs.spring.container.basic;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolverProperties;
import com.jashmore.sqs.resolver.batching.StaticBatchingMessageResolverProperties;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.AbstractQueueAnnotationWrapper;
import com.jashmore.sqs.spring.IdentifiableMessageListenerContainer;
import com.jashmore.sqs.spring.QueueWrapper;
import com.jashmore.sqs.spring.QueueWrapperInitialisationException;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.queue.QueueResolverService;
import com.jashmore.sqs.spring.util.IdentifierUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;

/**
 * {@link QueueWrapper} that will wrap methods annotated with {@link QueueListener @QueueListener} with some predefined
 * implementations of the framework.
 */
@Slf4j
@AllArgsConstructor
public class QueueListenerWrapper extends AbstractQueueAnnotationWrapper<QueueListener> {
    private final ArgumentResolverService argumentResolverService;
    private final SqsAsyncClientProvider sqsAsyncClientProvider;
    private final QueueResolverService queueResolverService;
    private final Environment environment;

    @Override
    protected Class<QueueListener> getAnnotationClass() {
        return QueueListener.class;
    }

    @Override
    protected IdentifiableMessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method, final QueueListener annotation) {
        final SqsAsyncClient sqsAsyncClient = getSqsAsyncClient(annotation.sqsClient());

        final QueueProperties queueProperties = QueueProperties.builder()
                .queueUrl(queueResolverService.resolveQueueUrl(sqsAsyncClient, annotation.value()))
                .build();

        final int concurrencyLevel = getConcurrencyLevel(annotation);

        final MessageRetriever messageRetriever = buildMessageRetriever(annotation, queueProperties, sqsAsyncClient);

        final MessageResolver messageResolver = buildMessageResolver(annotation, queueProperties, sqsAsyncClient);

        final MessageProcessor messageProcessor = new DefaultMessageProcessor(argumentResolverService, queueProperties, sqsAsyncClient,
                messageResolver, method, bean);

        final String identifier;
        if (StringUtils.isEmpty(annotation.identifier().trim())) {
            identifier = IdentifierUtils.buildIdentifierForMethod(bean.getClass(), method);
        } else {
            identifier = annotation.identifier().trim();
        }

        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(concurrencyLevel)
                        .threadNameFormat(identifier + "-%d")
                        .build()
        );

        return IdentifiableMessageListenerContainer.builder()
                .identifier(identifier)
                .container(new SimpleMessageListenerContainer(messageRetriever, messageBroker, messageResolver))
                .build();
    }

    private MessageRetriever buildMessageRetriever(final QueueListener annotation,
                                                   final QueueProperties queueProperties,
                                                   final SqsAsyncClient sqsAsyncClient) {
        return new BatchingMessageRetriever(queueProperties, sqsAsyncClient, batchingMessageRetrieverProperties(annotation));
    }

    @VisibleForTesting
    BatchingMessageRetrieverProperties batchingMessageRetrieverProperties(final QueueListener annotation) {
        return StaticBatchingMessageRetrieverProperties.builder()
                .messageVisibilityTimeoutInSeconds(getMessageVisibilityTimeoutInSeconds(annotation))
                .messageRetrievalPollingPeriodInMs(getMaxPeriodBetweenBatchesInMs(annotation))
                .numberOfThreadsWaitingTrigger(getBatchSize(annotation))
                .build();
    }

    private MessageResolver buildMessageResolver(final QueueListener annotation,
                                                 final QueueProperties queueProperties,
                                                 final SqsAsyncClient sqsAsyncClient) {
        final BatchingMessageResolverProperties batchingMessageResolverProperties = batchingMessageResolverProperties(annotation);

        return new BatchingMessageResolver(queueProperties, sqsAsyncClient, batchingMessageResolverProperties);
    }

    @VisibleForTesting
    BatchingMessageResolverProperties batchingMessageResolverProperties(final QueueListener annotation) {
        return StaticBatchingMessageResolverProperties.builder()
                .bufferingSizeLimit(getBatchSize(annotation))
                .bufferingTimeInMs(getMaxPeriodBetweenBatchesInMs(annotation))
                .build();
    }

    private int getConcurrencyLevel(final QueueListener annotation) {
        if (StringUtils.isEmpty(annotation.concurrencyLevelString())) {
            return annotation.concurrencyLevel();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.concurrencyLevelString()));
    }

    private int getBatchSize(final QueueListener annotation) {
        if (StringUtils.isEmpty(annotation.batchSizeString())) {
            return annotation.batchSize();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.batchSizeString()));
    }


    private long getMaxPeriodBetweenBatchesInMs(final QueueListener annotation) {
        if (StringUtils.isEmpty(annotation.maxPeriodBetweenBatchesInMsString())) {
            return annotation.maxPeriodBetweenBatchesInMs();
        }

        return Long.parseLong(environment.resolvePlaceholders(annotation.maxPeriodBetweenBatchesInMsString()));
    }

    private int getMessageVisibilityTimeoutInSeconds(final QueueListener annotation) {
        if (StringUtils.isEmpty(annotation.messageVisibilityTimeoutInSecondsString())) {
            return annotation.messageVisibilityTimeoutInSeconds();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.messageVisibilityTimeoutInSecondsString()));
    }

    private SqsAsyncClient getSqsAsyncClient(final String sqsClient) {
        if (StringUtils.isEmpty(sqsClient)) {
            return sqsAsyncClientProvider.getDefaultClient()
                    .orElseThrow(() -> new QueueWrapperInitialisationException("Expected the default SQS Client but there is none"));
        }

        return sqsAsyncClientProvider.getClient(sqsClient)
                .orElseThrow(() -> new QueueWrapperInitialisationException("Expected a client with id '" + sqsClient + "' but none were found"));
    }
}
