package com.jashmore.sqs.annotations.core.basic;

import com.jashmore.sqs.annotations.container.AnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.client.QueueResolver;
import com.jashmore.sqs.client.SqsAsyncClientProvider;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainerFactory;
import com.jashmore.sqs.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainer;
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainerProperties;
import com.jashmore.sqs.processor.DecoratingMessageProcessorFactory;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * {@link MessageListenerContainerFactory} that will wrap methods annotated with
 * {@link QueueListener @QueueListener} with some predefined implementations of the framework.
 */
public class BasicAnnotationMessageListenerContainerFactory implements MessageListenerContainerFactory {

    private final AnnotationMessageListenerContainerFactory<QueueListener> delegate;

    public BasicAnnotationMessageListenerContainerFactory(
        final ArgumentResolverService argumentResolverService,
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final QueueResolver queueResolver,
        final QueueListenerParser queueListenerParser,
        final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
    ) {
        this.delegate =
            new AnnotationMessageListenerContainerFactory<>(
                QueueListener.class,
                QueueListener::identifier,
                QueueListener::sqsClient,
                QueueListener::value,
                queueResolver,
                sqsAsyncClientProvider,
                decoratingMessageProcessorFactory,
                argumentResolverService,
                details -> {
                    final BatchingMessageListenerContainerProperties properties = queueListenerParser.parse(details.annotation);
                    return new BatchingMessageListenerContainer(
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
    public Optional<MessageListenerContainer> buildContainer(final Object bean, final Method method)
        throws MessageListenerContainerInitialisationException {
        return this.delegate.buildContainer(bean, method);
    }
}
