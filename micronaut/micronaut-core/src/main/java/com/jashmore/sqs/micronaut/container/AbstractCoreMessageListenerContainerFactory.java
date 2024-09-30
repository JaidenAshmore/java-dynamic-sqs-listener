package com.jashmore.sqs.micronaut.container;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.micronaut.client.SqsAsyncClientProvider;
import com.jashmore.sqs.micronaut.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.micronaut.queue.QueueResolver;
import com.jashmore.sqs.micronaut.util.IdentifierUtils;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import io.micronaut.core.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Abstract Factory for building the core {@link MessageListenerContainer}s which handles actions like calculating the identifier,
 * getting the SQS client etc.
 *
 * @param <A> the Spring annotation that is used to define the container's properties
 * @param <P> the properties object that configures the {@link MessageListenerContainer}
 */
public abstract class AbstractCoreMessageListenerContainerFactory<A extends Annotation, P>
    extends AbstractAnnotationMessageListenerContainerFactory<A> {

    private final SqsAsyncClientProvider sqsAsyncClientProvider;
    private final QueueResolver queueResolver;
    private final CoreAnnotationParser<A, P> annotationParser;
    private final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory;
    private final ArgumentResolverService argumentResolverService;

    protected AbstractCoreMessageListenerContainerFactory(
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final QueueResolver queueResolver,
        final CoreAnnotationParser<A, P> annotationParser,
        final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory,
        final ArgumentResolverService argumentResolverService
    ) {
        this.sqsAsyncClientProvider = sqsAsyncClientProvider;
        this.queueResolver = queueResolver;
        this.annotationParser = annotationParser;
        this.decoratingMessageProcessorFactory = decoratingMessageProcessorFactory;
        this.argumentResolverService = argumentResolverService;
    }

    /**
     * Given the parsed information from the annotation, build the actual container.
     *
     * @param identifier the identifier that uniquely defines this container
     * @param sqsAsyncClient the client that will communicate to SQS in this container
     * @param queueProperties the queue properties
     * @param containerProperties the specific configuration properties for the container
     * @param messageProcessorSupplier the message processor supplier that will execute the message listener
     * @return the constructed container from these properties
     */
    protected abstract MessageListenerContainer buildContainer(
        String identifier,
        SqsAsyncClient sqsAsyncClient,
        QueueProperties queueProperties,
        P containerProperties,
        Supplier<MessageProcessor> messageProcessorSupplier
    );

    /**
     * Obtain the optional identifier from the annotation.
     *
     * <p>If this is null or empty, the actual identifier will be constructed from the message listener's message signature.
     *
     * @param annotation the annotation to process
     * @return the optional identifier
     */
    @Nullable
    protected abstract String getIdentifier(A annotation);

    /**
     * Get the queue name or URL from the annotation.
     *
     * @param annotation the annotation to process
     * @return the queue name or URL
     */
    protected abstract String getQueueNameOrUrl(A annotation);

    /**
     * Get the optional SQS Client identifier.
     *
     * <p>If this is null or empty, the default SQS client is used.
     *
     * @param annotation the annotation to process
     * @return the optional client identifier
     * @see SqsAsyncClientProvider for what will provide the SQS Client
     */
    @Nullable
    protected abstract String getSqsClientIdentifier(A annotation);

    @Override
    protected MessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method, final A annotation) {
        final SqsAsyncClient sqsAsyncClient = getSqsAsyncClient(annotation);
        final QueueProperties queueProperties = getQueueProperties(sqsAsyncClient, annotation);
        final P properties = annotationParser.parse(annotation);
        final String identifier = IdentifierUtils.buildIdentifierForMethod(getIdentifier(annotation), bean.getClass(), method);
        final Supplier<MessageProcessor> messageProcessorSupplier = () ->
            decoratingMessageProcessorFactory.decorateMessageProcessor(
                sqsAsyncClient,
                identifier,
                queueProperties,
                bean,
                method,
                new CoreMessageProcessor(argumentResolverService, queueProperties, sqsAsyncClient, method, bean)
            );

        return buildContainer(identifier, sqsAsyncClient, queueProperties, properties, messageProcessorSupplier);
    }

    private SqsAsyncClient getSqsAsyncClient(A annotation) {
        final String sqsClient = getSqsClientIdentifier(annotation);

        if (!StringUtils.hasText(sqsClient)) {
            return sqsAsyncClientProvider
                .getDefaultClient()
                .orElseThrow(() -> new MessageListenerContainerInitialisationException("Expected the default SQS Client but there is none")
                );
        }

        return sqsAsyncClientProvider
            .getClient(sqsClient)
            .orElseThrow(() ->
                new MessageListenerContainerInitialisationException("Expected a client with id '" + sqsClient + "' but none were found")
            );
    }

    private QueueProperties getQueueProperties(final SqsAsyncClient sqsAsyncClient, final A annotation) {
        final String queueNameOrUrl = getQueueNameOrUrl(annotation);

        return QueueProperties.builder().queueUrl(queueResolver.resolveQueueUrl(sqsAsyncClient, queueNameOrUrl)).build();
    }
}
