package com.jashmore.sqs.annotations.container;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.client.QueueResolver;
import com.jashmore.sqs.client.SqsAsyncClientProvider;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainerFactory;
import com.jashmore.sqs.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.util.annotation.AnnotationUtils;
import com.jashmore.sqs.util.identifier.IdentifierUtils;
import com.jashmore.sqs.util.string.StringUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Builder;
import org.immutables.value.Value;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * {@link MessageListenerContainerFactory} that can be used to build against an annotated method.
 *
 * @param <A> annotation that is applied on the method
 */
public class AnnotationMessageListenerContainerFactory<A extends Annotation> implements MessageListenerContainerFactory {

    private final Class<A> annotationClass;
    private final Function<A, String> identifierMapper;
    private final Function<A, String> sqsClientIdentifier;
    private final Function<A, String> queueNameOrUrlMapper;
    private final QueueResolver queueResolver;
    private final SqsAsyncClientProvider sqsAsyncClientProvider;
    private final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory;
    private final ArgumentResolverService argumentResolverService;
    private final Function<AnnotationDetails<A>, MessageListenerContainer> containerFactory;

    /**
     * Constructor.
     *
     * @param annotationClass the class instance of the annotation
     * @param identifierMapper to convert an annotation to the identifier of the listener
     * @param sqsClientIdentifierMapper to convert an annotation to the SQS Client identifier
     * @param queueNameOrUrlMapper to convert an annotation to the Queue URL or name
     * @param queueResolver to resolve queue names to a URL
     * @param sqsAsyncClientProvider the method for obtaining a SQS client from the identifier
     * @param decoratingMessageProcessorFactory to wrap the message processing with any decorators
     * @param argumentResolverService to map the parameters of the method to values in the message
     * @param containerFactory converts details about the annotation to the final {@link MessageListenerContainer}
     */
    public AnnotationMessageListenerContainerFactory(
        final Class<A> annotationClass,
        final Function<A, String> identifierMapper,
        final Function<A, String> sqsClientIdentifierMapper,
        final Function<A, String> queueNameOrUrlMapper,
        final QueueResolver queueResolver,
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory,
        final ArgumentResolverService argumentResolverService,
        final Function<AnnotationDetails<A>, MessageListenerContainer> containerFactory
    ) {
        this.annotationClass = annotationClass;
        this.identifierMapper = identifierMapper;
        this.sqsClientIdentifier = sqsClientIdentifierMapper;
        this.queueNameOrUrlMapper = queueNameOrUrlMapper;
        this.queueResolver = queueResolver;
        this.sqsAsyncClientProvider = sqsAsyncClientProvider;
        this.decoratingMessageProcessorFactory = decoratingMessageProcessorFactory;
        this.argumentResolverService = argumentResolverService;
        this.containerFactory = containerFactory;
    }

    @Override
    public Optional<MessageListenerContainer> buildContainer(final Object bean, final Method method)
        throws MessageListenerContainerInitialisationException {
        return AnnotationUtils
            .findMethodAnnotation(method, this.annotationClass)
            .map(annotation -> {
                final SqsAsyncClient sqsAsyncClient = getSqsAsyncClient(annotation);
                final QueueProperties queueProperties = QueueProperties
                    .builder()
                    .queueUrl(queueResolver.resolveQueueUrl(sqsAsyncClient, queueNameOrUrlMapper.apply(annotation)))
                    .build();
                final String identifier = IdentifierUtils.buildIdentifierForMethod(
                    identifierMapper.apply(annotation),
                    bean.getClass(),
                    method
                );

                final Supplier<MessageProcessor> messageProcessorSupplier = () ->
                    decoratingMessageProcessorFactory.decorateMessageProcessor(
                        sqsAsyncClient,
                        identifier,
                        queueProperties,
                        bean,
                        method,
                        new CoreMessageProcessor(argumentResolverService, queueProperties, sqsAsyncClient, method, bean)
                    );

                return containerFactory.apply(
                    AnnotationDetails
                        .<A>builder()
                        .identifier(identifier)
                        .queueProperties(queueProperties)
                        .sqsAsyncClient(sqsAsyncClient)
                        .messageProcessorSupplier(messageProcessorSupplier)
                        .annotation(annotation)
                        .build()
                );
            });
    }

    private SqsAsyncClient getSqsAsyncClient(final A annotation) {
        final String sqsClient = sqsClientIdentifier.apply(annotation);

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

    @Value
    @Builder
    public static class AnnotationDetails<A extends Annotation> {

        public String identifier;
        public SqsAsyncClient sqsAsyncClient;
        public QueueProperties queueProperties;
        public Supplier<MessageProcessor> messageProcessorSupplier;
        public A annotation;
    }
}
