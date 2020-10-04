package com.jashmore.sqs.spring.container.basic;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainer;
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainerProperties;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.AbstractAnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.spring.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.spring.queue.QueueResolver;
import com.jashmore.sqs.spring.util.IdentifierUtils;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * {@link com.jashmore.sqs.spring.container.MessageListenerContainerFactory} that will wrap methods annotated with
 * {@link QueueListener @QueueListener} with some predefined implementations of the framework.
 */
@Slf4j
public class BasicMessageListenerContainerFactory extends AbstractAnnotationMessageListenerContainerFactory<QueueListener> {
    private final ArgumentResolverService argumentResolverService;
    private final SqsAsyncClientProvider sqsAsyncClientProvider;
    private final QueueResolver queueResolver;
    private final Environment environment;
    private final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory;

    public BasicMessageListenerContainerFactory(
        final ArgumentResolverService argumentResolverService,
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final QueueResolver queueResolver,
        final Environment environment,
        final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
    ) {
        this.argumentResolverService = argumentResolverService;
        this.sqsAsyncClientProvider = sqsAsyncClientProvider;
        this.queueResolver = queueResolver;
        this.environment = environment;
        this.decoratingMessageProcessorFactory = decoratingMessageProcessorFactory;
    }

    @Override
    protected Class<QueueListener> getAnnotationClass() {
        return QueueListener.class;
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected MessageListenerContainer wrapMethodContainingAnnotation(
        final Object bean,
        final Method method,
        final QueueListener annotation
    ) {
        final SqsAsyncClient sqsAsyncClient = getSqsAsyncClient(annotation.sqsClient());

        final QueueProperties queueProperties = QueueProperties
            .builder()
            .queueUrl(queueResolver.resolveQueueUrl(sqsAsyncClient, annotation.value()))
            .build();

        final String identifier = IdentifierUtils.buildIdentifierForMethod(annotation.identifier(), bean.getClass(), method);
        return new BatchingMessageListenerContainer(
            identifier,
            queueProperties,
            sqsAsyncClient,
            buildProcessorSupplier(identifier, queueProperties, sqsAsyncClient, bean, method),
            buildProperties(annotation)
        );
    }

    private BatchingMessageListenerContainerProperties buildProperties(final QueueListener annotation) {
        final int concurrencyLevel = getConcurrencyLevel(annotation);
        final int batchSize = getBatchSize(annotation);
        final Duration batchingPeriod = getMaxPeriodBetweenBatches(annotation);
        final Duration messageVisibilityTimeout = getMessageVisibilityTimeout(annotation);
        final boolean processAnyExtraRetrievedMessagesOnShutdown = annotation.processAnyExtraRetrievedMessagesOnShutdown();
        final boolean interruptThreadsProcessingMessagesOnShutdown = annotation.interruptThreadsProcessingMessagesOnShutdown();
        return new BatchingMessageListenerContainerProperties() {

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
            @Max(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS)
            @Override
            public int batchSize() {
                return batchSize;
            }

            @Nullable
            @Positive
            @Override
            public Duration getBatchingPeriod() {
                return batchingPeriod;
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

    private Duration getMaxPeriodBetweenBatches(final QueueListener annotation) {
        if (StringUtils.isEmpty(annotation.batchingPeriodInMsString())) {
            return Duration.ofMillis(annotation.batchingPeriodInMs());
        }

        return Duration.ofMillis(Long.parseLong(environment.resolvePlaceholders(annotation.batchingPeriodInMsString())));
    }

    private Duration getMessageVisibilityTimeout(final QueueListener annotation) {
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
