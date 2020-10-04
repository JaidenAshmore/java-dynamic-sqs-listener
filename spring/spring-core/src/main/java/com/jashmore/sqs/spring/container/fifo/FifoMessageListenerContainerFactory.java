package com.jashmore.sqs.spring.container.fifo;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainer;
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainerProperties;
import com.jashmore.sqs.container.fifo.ImmutableFifoMessageListenerContainerProperties;
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
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * {@link MessageListenerContainerFactory} that will wrap methods annotated with {@link FifoQueueListener @FifoQueueListener} with
 * a {@link FifoMessageListenerContainer} that will automatically handle processing of messages coming from a FIFO SQS Queue.
 *
 * <p>A Spring bean needs to have a method annotated with this annotation like:
 *
 * <pre class="code">
 *     &#064;FifoQueueListener(value = "test-queue.fifo", concurrencyLevel = 10, maximumMessagesInMessageGroup = 2)
 *     public void myMessageProcessor(Message message) {
 * </pre>
 */
public class FifoMessageListenerContainerFactory extends AbstractAnnotationMessageListenerContainerFactory<FifoQueueListener> {
    private final ArgumentResolverService argumentResolverService;
    private final SqsAsyncClientProvider sqsAsyncClientProvider;
    private final QueueResolver queueResolver;
    private final Environment environment;
    private final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory;

    public FifoMessageListenerContainerFactory(
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
    protected Class<FifoQueueListener> getAnnotationClass() {
        return FifoQueueListener.class;
    }

    @Override
    protected MessageListenerContainer wrapMethodContainingAnnotation(
        final Object bean,
        final Method method,
        final FifoQueueListener annotation
    ) {
        final SqsAsyncClient sqsAsyncClient = getSqsAsyncClient(annotation.sqsClient());

        final QueueProperties queueProperties = QueueProperties
            .builder()
            .queueUrl(queueResolver.resolveQueueUrl(sqsAsyncClient, annotation.value()))
            .build();

        final String identifier = IdentifierUtils.buildIdentifierForMethod(annotation.identifier(), bean.getClass(), method);

        return new FifoMessageListenerContainer(
            queueProperties,
            sqsAsyncClient,
            buildProcessorSupplier(identifier, queueProperties, sqsAsyncClient, bean, method),
            buildProperties(identifier, annotation)
        );
    }

    private FifoMessageListenerContainerProperties buildProperties(final String identifier, final FifoQueueListener annotation) {
        return ImmutableFifoMessageListenerContainerProperties
            .builder()
            .identifier(identifier)
            .concurrencyLevel(getConcurrencyLevel(annotation))
            .concurrencyPollingRate(null)
            .errorBackoffTime(null)
            .maximumCachedMessageGroups(getMaximumCachedMessageGroups(annotation))
            .messageVisibilityTimeout(getMessageVisibilityTimeout(annotation))
            .maximumMessagesInMessageGroup(getBatchSize(annotation))
            .interruptThreadsProcessingMessagesOnShutdown(annotation.interruptThreadsProcessingMessagesOnShutdown())
            .tryAndProcessAnyExtraRetrievedMessagesOnShutdown(annotation.tryAndProcessAnyExtraRetrievedMessagesOnShutdown())
            .build();
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

    private int getConcurrencyLevel(final FifoQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.concurrencyLevelString())) {
            return annotation.concurrencyLevel();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.concurrencyLevelString()));
    }

    private int getBatchSize(final FifoQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.maximumMessagesInMessageGroupString())) {
            return annotation.maximumMessagesInMessageGroup();
        }

        return Integer.parseInt(environment.resolvePlaceholders(annotation.maximumMessagesInMessageGroupString()));
    }

    private Duration getMessageVisibilityTimeout(final FifoQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.messageVisibilityTimeoutInSecondsString())) {
            return Duration.ofSeconds(annotation.messageVisibilityTimeoutInSeconds());
        }

        return Duration.ofSeconds(Integer.parseInt(environment.resolvePlaceholders(annotation.messageVisibilityTimeoutInSecondsString())));
    }

    private int getMaximumCachedMessageGroups(final FifoQueueListener annotation) {
        if (StringUtils.isEmpty(annotation.maximumCachedMessageGroupsString())) {
            return annotation.maximumCachedMessageGroups();
        }
        return Integer.parseInt(environment.resolvePlaceholders(annotation.maximumCachedMessageGroupsString()));
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
