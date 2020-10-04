package com.jashmore.sqs.spring.container.prefetch;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainer;
import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainerProperties;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
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
        return new PrefetchingMessageListenerContainer(
            identifier,
            queueProperties,
            sqsAsyncClient,
            buildProcessorSupplier(identifier, queueProperties, sqsAsyncClient, bean, method),
            buildProperties(annotation)
        );
    }

    private PrefetchingMessageListenerContainerProperties buildProperties(final PrefetchingQueueListener annotation) {
        final int concurrencyLevel = getConcurrencyLevel(annotation);
        final int desiredPrefetchedMessages = getDesiredMinPrefetchedMessages(annotation);
        final int maxPrefetchedMessages = getMaxPrefetchedMessages(annotation);
        final Duration messageVisibilityTimeout = getMessageVisibilityTimeout(annotation);
        final boolean processAnyExtraRetrievedMessagesOnShutdown = annotation.processAnyExtraRetrievedMessagesOnShutdown();
        final boolean interruptThreadsProcessingMessagesOnShutdown = annotation.interruptThreadsProcessingMessagesOnShutdown();
        return new PrefetchingMessageListenerContainerProperties() {

            @PositiveOrZero
            @Override
            public int concurrencyLevel() {
                return concurrencyLevel;
            }

            @Nullable
            @Override
            public Duration concurrencyPollingRate() {
                return null;
            }

            @Nullable
            @Override
            public Duration errorBackoffTime() {
                return null;
            }

            @Positive
            @Override
            public int desiredMinPrefetchedMessages() {
                return desiredPrefetchedMessages;
            }

            @Positive
            @Override
            public int maxPrefetchedMessages() {
                return maxPrefetchedMessages;
            }

            @Nullable
            @Positive
            @Override
            public Duration messageVisibilityTimeout() {
                return messageVisibilityTimeout;
            }

            @Override
            public boolean processAnyExtraRetrievedMessagesOnShutdown() {
                return processAnyExtraRetrievedMessagesOnShutdown;
            }

            @Override
            public boolean interruptThreadsProcessingMessagesOnShutdown() {
                return interruptThreadsProcessingMessagesOnShutdown;
            }
        };
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
