package com.jashmore.sqs.spring.container.basic;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainer;
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainerProperties;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.AbstractCoreMessageListenerContainerFactory;
import com.jashmore.sqs.spring.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.spring.queue.QueueResolver;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * {@link com.jashmore.sqs.spring.container.MessageListenerContainerFactory} that will wrap methods annotated with
 * {@link QueueListener @QueueListener} with some predefined implementations of the framework.
 */
@Slf4j
public class BasicMessageListenerContainerFactory
    extends AbstractCoreMessageListenerContainerFactory<QueueListener, BatchingMessageListenerContainerProperties> {

    public BasicMessageListenerContainerFactory(
        final ArgumentResolverService argumentResolverService,
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final QueueResolver queueResolver,
        final QueueListenerParser queueListenerParser,
        final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
    ) {
        super(sqsAsyncClientProvider, queueResolver, queueListenerParser, decoratingMessageProcessorFactory, argumentResolverService);
    }

    @Override
    protected Class<QueueListener> getAnnotationClass() {
        return QueueListener.class;
    }

    @Override
    protected MessageListenerContainer buildContainer(
        final String identifier,
        final SqsAsyncClient sqsAsyncClient,
        final QueueProperties queueProperties,
        final BatchingMessageListenerContainerProperties containerProperties,
        final Supplier<MessageProcessor> messageProcessorSupplier
    ) {
        return new BatchingMessageListenerContainer(
            identifier,
            queueProperties,
            sqsAsyncClient,
            messageProcessorSupplier,
            containerProperties
        );
    }

    @Override
    protected String getIdentifier(final QueueListener annotation) {
        return annotation.identifier();
    }

    @Override
    protected String getQueueNameOrUrl(final QueueListener annotation) {
        return annotation.value();
    }

    @Override
    protected String getSqsClientIdentifier(final QueueListener annotation) {
        return annotation.sqsClient();
    }
}
