package com.jashmore.sqs.spring.container.prefetch;

import com.jashmore.documentation.annotations.VisibleForTesting;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.StaticCoreMessageListenerContainerProperties;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.AbstractAnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.MessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.spring.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.spring.queue.QueueResolver;
import com.jashmore.sqs.spring.util.IdentifierUtils;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * {@link MessageListenerContainerFactory} that will wrap methods annotated with {@link PrefetchingQueueListener @PrefetchingQueueListener} with
 * some predefined implementations of the framework.
 */
@Slf4j
@RequiredArgsConstructor
public class PrefetchingMessageListenerContainerFactory
    extends AbstractAnnotationMessageListenerContainerFactory<PrefetchingQueueListener> {
    private final ArgumentResolverService argumentResolverService;
    private final SqsAsyncClientProvider sqsAsyncClientProvider;
    private final QueueResolver queueResolver;
    private final Environment environment;
    private final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory;

    @Override
    protected Class<PrefetchingQueueListener> getAnnotationClass() {
        return PrefetchingQueueListener.class;
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected MessageListenerContainer wrapMethodContainingAnnotation(
        final Object bean,
        final Method method,
        final PrefetchingQueueListener annotation
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
            buildMessageResolverSupplier(queueProperties, sqsAsyncClient),
            StaticCoreMessageListenerContainerProperties
                .builder()
                .shouldProcessAnyExtraRetrievedMessagesOnShutdown(annotation.processAnyExtraRetrievedMessagesOnShutdown())
                .shouldInterruptThreadsProcessingMessagesOnShutdown(annotation.interruptThreadsProcessingMessagesOnShutdown())
                .build()
        );
    }

    private Supplier<MessageBroker> buildMessageBrokerSupplier(PrefetchingQueueListener annotation) {
        final int concurrencyLevel = getConcurrencyLevel(annotation);

        return () ->
            new ConcurrentMessageBroker(StaticConcurrentMessageBrokerProperties.builder().concurrencyLevel(concurrencyLevel).build());
    }

    private Supplier<MessageResolver> buildMessageResolverSupplier(
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient
    ) {
        return () -> new BatchingMessageResolver(queueProperties, sqsAsyncClient);
    }

    private Supplier<MessageProcessor> buildProcessorSupplier(
        final String identifier,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient,
        final Object bean,
        final Method method
    ) {
        return () ->
            decoratingMessageProcessorFactory.decorateMessageProcessor(
                sqsAsyncClient,
                identifier,
                queueProperties,
                bean,
                method,
                new CoreMessageProcessor(argumentResolverService, queueProperties, sqsAsyncClient, method, bean)
            );
    }

    private int getConcurrencyLevel(final PrefetchingQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.concurrencyLevelString())) {
            return annotation.concurrencyLevel();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.concurrencyLevelString()));
    }

    private int getMaxPrefetchedMessages(final PrefetchingQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.maxPrefetchedMessagesString())) {
            return annotation.maxPrefetchedMessages();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.maxPrefetchedMessagesString()));
    }

    private Duration getMessageVisibilityTimeout(final PrefetchingQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.messageVisibilityTimeoutInSecondsString())) {
            return Duration.ofSeconds(annotation.messageVisibilityTimeoutInSeconds());
        }

        return Duration.ofSeconds(Integer.parseInt(environment.resolvePlaceholders(annotation.messageVisibilityTimeoutInSecondsString())));
    }

    private int getDesiredMinPrefetchedMessages(final PrefetchingQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.desiredMinPrefetchedMessagesString())) {
            return annotation.desiredMinPrefetchedMessages();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.desiredMinPrefetchedMessagesString()));
    }

    @VisibleForTesting
    PrefetchingMessageRetrieverProperties buildMessageRetrieverProperties(final PrefetchingQueueListener annotation) {
        return StaticPrefetchingMessageRetrieverProperties
            .builder()
            .desiredMinPrefetchedMessages(getDesiredMinPrefetchedMessages(annotation))
            .maxPrefetchedMessages(getMaxPrefetchedMessages(annotation))
            .messageVisibilityTimeout(getMessageVisibilityTimeout(annotation))
            .build();
    }

    private Supplier<MessageRetriever> buildMessageRetrieverSupplier(
        final PrefetchingQueueListener annotation,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient
    ) {
        final PrefetchingMessageRetrieverProperties properties = buildMessageRetrieverProperties(annotation);
        return () -> new PrefetchingMessageRetriever(sqsAsyncClient, queueProperties, properties);
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
