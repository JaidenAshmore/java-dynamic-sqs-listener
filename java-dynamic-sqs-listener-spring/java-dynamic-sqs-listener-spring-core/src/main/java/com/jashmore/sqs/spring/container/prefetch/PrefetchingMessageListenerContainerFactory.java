package com.jashmore.sqs.spring.container.prefetch;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.individual.IndividualMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.AbstractAnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.MessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.spring.queue.QueueResolver;
import com.jashmore.sqs.spring.util.IdentifierUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;

/**
 * {@link MessageListenerContainerFactory} that will wrap methods annotated with {@link PrefetchingQueueListener @PrefetchingQueueListener} with
 * some predefined implementations of the framework.
 */
@Slf4j
@RequiredArgsConstructor
public class PrefetchingMessageListenerContainerFactory extends AbstractAnnotationMessageListenerContainerFactory<PrefetchingQueueListener> {
    private final ArgumentResolverService argumentResolverService;
    private final SqsAsyncClientProvider sqsAsyncClientProvider;
    private final QueueResolver queueResolver;
    private final Environment environment;

    @Override
    protected Class<PrefetchingQueueListener> getAnnotationClass() {
        return PrefetchingQueueListener.class;
    }

    @Override
    protected MessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method,
                                                                      final PrefetchingQueueListener annotation) {
        final SqsAsyncClient sqsAsyncClient = getSqsAsyncClient(annotation.sqsClient());

        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(queueResolver.resolveQueueUrl(sqsAsyncClient, annotation.value()))
                .build();

        final MessageRetriever messageRetriever = buildMessageRetriever(annotation, queueProperties, sqsAsyncClient);
        final MessageResolver messageResolver = new IndividualMessageResolver(queueProperties, sqsAsyncClient);
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(argumentResolverService, queueProperties,
                sqsAsyncClient, messageResolver, method, bean);

        final String identifier;
        if (StringUtils.isEmpty(annotation.identifier().trim())) {
            identifier = IdentifierUtils.buildIdentifierForMethod(bean.getClass(), method);
        } else {
            identifier = annotation.identifier().trim();
        }

        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                StaticConcurrentMessageBrokerProperties
                        .builder()
                        .concurrencyLevel(getConcurrencyLevel(annotation))
                        .threadNameFormat(identifier + "-%d")
                        .build()
        );

        return new SimpleMessageListenerContainer(identifier, messageRetriever, messageBroker, messageResolver);
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

    private int getMessageVisibilityTimeoutInSeconds(final PrefetchingQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.messageVisibilityTimeoutInSecondsString())) {
            return annotation.messageVisibilityTimeoutInSeconds();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.messageVisibilityTimeoutInSecondsString()));
    }

    private int getDesiredMinPrefetchedMessages(final PrefetchingQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.desiredMinPrefetchedMessagesString())) {
            return annotation.desiredMinPrefetchedMessages();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.desiredMinPrefetchedMessagesString()));
    }

    @VisibleForTesting
    PrefetchingMessageRetrieverProperties buildMessageRetrieverProperties(final PrefetchingQueueListener annotation) {
        return StaticPrefetchingMessageRetrieverProperties.builder()
                .desiredMinPrefetchedMessages(getDesiredMinPrefetchedMessages(annotation))
                .maxPrefetchedMessages(getMaxPrefetchedMessages(annotation))
                .messageVisibilityTimeoutInSeconds(getMessageVisibilityTimeoutInSeconds(annotation))
                .build();
    }

    private MessageRetriever buildMessageRetriever(final PrefetchingQueueListener annotation,
                                                   final QueueProperties queueProperties,
                                                   final SqsAsyncClient sqsAsyncClient) {
        return new PrefetchingMessageRetriever(sqsAsyncClient, queueProperties, buildMessageRetrieverProperties(annotation));
    }

    private SqsAsyncClient getSqsAsyncClient(final String sqsClient) {
        if (StringUtils.isEmpty(sqsClient)) {
            return sqsAsyncClientProvider.getDefaultClient()
                    .orElseThrow(() -> new MessageListenerContainerInitialisationException("Expected the default SQS Client but there is none"));
        }

        return sqsAsyncClientProvider.getClient(sqsClient)
                .orElseThrow(() -> new MessageListenerContainerInitialisationException("Expected a client with id '" + sqsClient + "' but none were found"));
    }
}
