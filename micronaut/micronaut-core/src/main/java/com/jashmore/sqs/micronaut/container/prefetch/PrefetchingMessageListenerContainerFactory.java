package com.jashmore.sqs.micronaut.container.prefetch;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainer;
import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainerProperties;
import com.jashmore.sqs.micronaut.client.SqsAsyncClientProvider;
import com.jashmore.sqs.micronaut.container.AbstractCoreMessageListenerContainerFactory;
import com.jashmore.sqs.micronaut.container.MessageListenerContainerFactory;
import com.jashmore.sqs.micronaut.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.micronaut.queue.QueueResolver;
import com.jashmore.sqs.processor.MessageProcessor;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.function.Supplier;

/**
 * {@link MessageListenerContainerFactory} that will wrap methods annotated with {@link PrefetchingQueueListener @PrefetchingQueueListener} with
 * some predefined implementations of the framework.
 */
public class PrefetchingMessageListenerContainerFactory
    extends AbstractCoreMessageListenerContainerFactory<PrefetchingQueueListener, PrefetchingMessageListenerContainerProperties> {

    public PrefetchingMessageListenerContainerFactory(
        final ArgumentResolverService argumentResolverService,
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final QueueResolver queueResolver,
        final PrefetchingQueueListenerParser annotationParser,
        final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
    ) {
        super(sqsAsyncClientProvider, queueResolver, annotationParser, decoratingMessageProcessorFactory, argumentResolverService);
    }

    @Override
    protected Class<PrefetchingQueueListener> getAnnotationClass() {
        return PrefetchingQueueListener.class;
    }

    @Override
    protected MessageListenerContainer buildContainer(
        final String identifier,
        final SqsAsyncClient sqsAsyncClient,
        final QueueProperties queueProperties,
        final PrefetchingMessageListenerContainerProperties containerProperties,
        final Supplier<MessageProcessor> messageProcessorSupplier
    ) {
        return new PrefetchingMessageListenerContainer(
            identifier,
            queueProperties,
            sqsAsyncClient,
            messageProcessorSupplier,
            containerProperties
        );
    }

    @Override
    protected String getIdentifier(PrefetchingQueueListener annotation) {
        return annotation.identifier();
    }

    @Override
    protected String getQueueNameOrUrl(PrefetchingQueueListener annotation) {
        return annotation.value();
    }

    @Override
    protected String getSqsClientIdentifier(PrefetchingQueueListener annotation) {
        return annotation.sqsClient();
    }
}
