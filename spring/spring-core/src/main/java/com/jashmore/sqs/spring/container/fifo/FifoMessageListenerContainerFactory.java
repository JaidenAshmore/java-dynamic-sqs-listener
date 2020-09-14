package com.jashmore.sqs.spring.container.fifo;

import com.jashmore.documentation.annotations.VisibleForTesting;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.grouping.GroupingMessageBroker;
import com.jashmore.sqs.broker.grouping.StaticGroupingMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.StaticCoreMessageListenerContainerProperties;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.DecoratingMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolverProperties;
import com.jashmore.sqs.resolver.batching.StaticBatchingMessageResolverProperties;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.AbstractAnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.spring.queue.QueueResolver;
import com.jashmore.sqs.spring.util.IdentifierUtils;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

/**
 *
 */
public class FifoMessageListenerContainerFactory extends AbstractAnnotationMessageListenerContainerFactory<FifoListener> {
    private final ArgumentResolverService argumentResolverService;
    private final SqsAsyncClientProvider sqsAsyncClientProvider;
    private final QueueResolver queueResolver;
    private final Environment environment;
    private final List<MessageProcessingDecorator> messageProcessingDecorators;

    public FifoMessageListenerContainerFactory(
        final ArgumentResolverService argumentResolverService,
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final QueueResolver queueResolver,
        final Environment environment,
        final List<MessageProcessingDecorator> messageProcessingDecorators
    ) {
        this.argumentResolverService = argumentResolverService;
        this.sqsAsyncClientProvider = sqsAsyncClientProvider;
        this.queueResolver = queueResolver;
        this.environment = environment;
        this.messageProcessingDecorators = messageProcessingDecorators;
    }

    @Override
    protected Class<FifoListener> getAnnotationClass() {
        return FifoListener.class;
    }

    @Override
    protected MessageListenerContainer wrapMethodContainingAnnotation(
        final Object bean,
        final Method method,
        final FifoListener annotation
    ) {
        final SqsAsyncClient sqsAsyncClient = getSqsAsyncClient(annotation.sqsClient());

        final QueueProperties queueProperties = QueueProperties
            .builder()
            .queueUrl(queueResolver.resolveQueueUrl(sqsAsyncClient, annotation.value()))
            .build();

        final String identifier = IdentifierUtils.buildIdentifierForMethod(annotation.identifier(), bean.getClass(), method);
        return new CoreMessageListenerContainer(
            identifier,
            buildMessageBrokerSupplier(annotation),
            buildMessageRetrieverSupplier(annotation, queueProperties, sqsAsyncClient),
            buildProcessorSupplier(identifier, queueProperties, sqsAsyncClient, bean, method),
            buildMessageResolver(annotation, queueProperties, sqsAsyncClient),
            StaticCoreMessageListenerContainerProperties
                .builder()
                .shouldProcessAnyExtraRetrievedMessagesOnShutdown(annotation.processAnyExtraRetrievedMessagesOnShutdown())
                .shouldInterruptThreadsProcessingMessagesOnShutdown(annotation.interruptThreadsProcessingMessagesOnShutdown())
                .build()
        );
    }

    private Supplier<MessageBroker> buildMessageBrokerSupplier(final FifoListener annotation) {
        final int concurrencyLevel = getConcurrencyLevel(annotation);
        return () ->
            new GroupingMessageBroker(
                StaticGroupingMessageBrokerProperties
                    .builder()
                    .concurrencyLevel(concurrencyLevel)
                    .maximumConcurrentMessageRetrieval(getBatchSize(annotation))
                    .groupingFunction(message -> message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID))
                    .build()
            );
    }

    private Supplier<MessageProcessor> buildProcessorSupplier(
        final String identifier,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient,
        final Object bean,
        final Method method
    ) {
        return () -> {
            final CoreMessageProcessor delegateProcessor = new CoreMessageProcessor(
                argumentResolverService,
                queueProperties,
                sqsAsyncClient,
                method,
                bean
            );
            if (messageProcessingDecorators.isEmpty()) {
                return delegateProcessor;
            } else {
                return new DecoratingMessageProcessor(identifier, queueProperties, messageProcessingDecorators, delegateProcessor);
            }
        };
    }

    private Supplier<MessageRetriever> buildMessageRetrieverSupplier(
        final FifoListener annotation,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient
    ) {
        final BatchingMessageRetrieverProperties properties = batchingMessageRetrieverProperties(annotation);
        return () -> new BatchingMessageRetriever(queueProperties, sqsAsyncClient, properties);
    }

    @VisibleForTesting
    BatchingMessageRetrieverProperties batchingMessageRetrieverProperties(final FifoListener annotation) {
        return StaticBatchingMessageRetrieverProperties
            .builder()
            .messageVisibilityTimeout(getMessageVisibilityTimeout(annotation))
            .batchingPeriod(getMaxPeriodBetweenBatches(annotation))
            .batchSize(getBatchSize(annotation))
            .build();
    }

    private Supplier<MessageResolver> buildMessageResolver(
        final FifoListener annotation,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient
    ) {
        final BatchingMessageResolverProperties batchingMessageResolverProperties = StaticBatchingMessageResolverProperties
            .builder()
            .bufferingSizeLimit(getBatchSize(annotation))
            .bufferingTime(getMaxPeriodBetweenBatches(annotation))
            .build();

        return () -> new BatchingMessageResolver(queueProperties, sqsAsyncClient, batchingMessageResolverProperties);
    }

    private int getConcurrencyLevel(final FifoListener annotation) {
        if (StringUtils.isEmpty(annotation.concurrencyLevelString())) {
            return annotation.concurrencyLevel();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.concurrencyLevelString()));
    }

    private int getBatchSize(final FifoListener annotation) {
        if (StringUtils.isEmpty(annotation.batchSizeString())) {
            return annotation.batchSize();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.batchSizeString()));
    }

    private Duration getMaxPeriodBetweenBatches(final FifoListener annotation) {
        if (StringUtils.isEmpty(annotation.batchingPeriodInMsString())) {
            return Duration.ofMillis(annotation.batchingPeriodInMs());
        }

        return Duration.ofMillis(Long.parseLong(environment.resolvePlaceholders(annotation.batchingPeriodInMsString())));
    }

    private Duration getMessageVisibilityTimeout(final FifoListener annotation) {
        if (StringUtils.isEmpty(annotation.messageVisibilityTimeoutInSecondsString())) {
            return Duration.ofSeconds(annotation.messageVisibilityTimeoutInSeconds());
        }

        return Duration.ofSeconds(Integer.parseInt(environment.resolvePlaceholders(annotation.messageVisibilityTimeoutInSecondsString())));
    }

    private SqsAsyncClient getSqsAsyncClient(final String sqsClient) {
        if (StringUtils.isEmpty(sqsClient)) {
            return sqsAsyncClientProvider
                .getDefaultClient()
                .orElseThrow(
                    () -> new MessageListenerContainerInitialisationException("Expected the default SQS Client but there is none")
                );
        }

        return sqsAsyncClientProvider
            .getClient(sqsClient)
            .orElseThrow(
                () ->
                    new MessageListenerContainerInitialisationException("Expected a client with id '" + sqsClient + "' but none were found")
            );
    }
}
