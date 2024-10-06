package com.jashmore.sqs.annotations.core.prefetch;

import com.jashmore.sqs.annotations.container.AnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.client.QueueResolver;
import com.jashmore.sqs.client.SqsAsyncClientProvider;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainerFactory;
import com.jashmore.sqs.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainer;
import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainerProperties;
import com.jashmore.sqs.processor.DecoratingMessageProcessorFactory;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * {@link MessageListenerContainerFactory} that will wrap methods annotated with {@link PrefetchingQueueListener @PrefetchingQueueListener} with
 * some predefined implementations of the framework.
 */
public class PrefetchingAnnotationMessageListenerContainerFactory implements MessageListenerContainerFactory {

    private final AnnotationMessageListenerContainerFactory<PrefetchingQueueListener> delegate;

    public PrefetchingAnnotationMessageListenerContainerFactory(
        final ArgumentResolverService argumentResolverService,
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final QueueResolver queueResolver,
        final PrefetchingQueueListenerParser annotationParser,
        final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
    ) {
        this.delegate =
            new AnnotationMessageListenerContainerFactory<>(
                PrefetchingQueueListener.class,
                PrefetchingQueueListener::identifier,
                PrefetchingQueueListener::sqsClient,
                PrefetchingQueueListener::value,
                queueResolver,
                sqsAsyncClientProvider,
                decoratingMessageProcessorFactory,
                argumentResolverService,
                details -> {
                    final PrefetchingMessageListenerContainerProperties properties = annotationParser.parse(details.annotation);
                    return new PrefetchingMessageListenerContainer(
                        details.identifier,
                        details.queueProperties,
                        details.sqsAsyncClient,
                        details.messageProcessorSupplier,
                        properties
                    );
                }
            );
    }

    @Override
    public Optional<MessageListenerContainer> buildContainer(Object bean, Method method)
        throws MessageListenerContainerInitialisationException {
        return this.delegate.buildContainer(bean, method);
    }
}
