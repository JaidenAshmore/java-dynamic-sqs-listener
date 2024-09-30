package com.jashmore.sqs.micronaut.processor;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.micronaut.decorator.MessageProcessingDecoratorFactory;
import com.jashmore.sqs.processor.DecoratingMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.util.collections.CollectionUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Factory used to wrap the provided {@link MessageProcessor} with a {@link DecoratingMessageProcessor} if there are
 * {@link MessageProcessingDecorator}s that should be applied.
 *
 * <p>These decorators may be global that should be applied to message listeners or {@link MessageProcessingDecorator} that should only be applied to a
 * specific message listener via a {@link MessageProcessingDecoratorFactory}.
 */
public class DecoratingMessageProcessorFactory {

    private final List<MessageProcessingDecorator> globalDecorators;
    private final List<MessageProcessingDecoratorFactory<? extends MessageProcessingDecorator>> decoratorFactories;

    public DecoratingMessageProcessorFactory(
        final List<MessageProcessingDecorator> globalDecorators,
        final List<MessageProcessingDecoratorFactory<? extends MessageProcessingDecorator>> decoratorFactories
    ) {
        this.globalDecorators = globalDecorators;
        this.decoratorFactories = decoratorFactories;
    }

    public MessageProcessor decorateMessageProcessor(
        final SqsAsyncClient sqsAsyncClient,
        final String identifier,
        final QueueProperties queueProperties,
        final Object bean,
        final Method method,
        final MessageProcessor delegate
    ) {
        final List<MessageProcessingDecorator> methodProcessingDecorators = decoratorFactories
            .stream()
            .map(decoratorFactory -> decoratorFactory.buildDecorator(sqsAsyncClient, queueProperties, identifier, bean, method))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

        if (globalDecorators.isEmpty() && methodProcessingDecorators.isEmpty()) {
            return delegate;
        }

        return new DecoratingMessageProcessor(
            identifier,
            queueProperties,
            CollectionUtils.immutableListFrom(globalDecorators, methodProcessingDecorators),
            delegate
        );
    }
}
