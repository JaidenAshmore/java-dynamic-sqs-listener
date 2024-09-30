package com.jashmore.sqs.micronaut.container.fifo;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainer;
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainerProperties;
import com.jashmore.sqs.micronaut.client.SqsAsyncClientProvider;
import com.jashmore.sqs.micronaut.container.AbstractCoreMessageListenerContainerFactory;
import com.jashmore.sqs.micronaut.container.MessageListenerContainerFactory;
import com.jashmore.sqs.micronaut.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.micronaut.queue.QueueResolver;
import com.jashmore.sqs.processor.MessageProcessor;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.function.Supplier;

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
public class FifoMessageListenerContainerFactory
    extends AbstractCoreMessageListenerContainerFactory<FifoQueueListener, FifoMessageListenerContainerProperties> {

    public FifoMessageListenerContainerFactory(
        final ArgumentResolverService argumentResolverService,
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final QueueResolver queueResolver,
        final FifoQueueListenerParser annotationParser,
        final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
    ) {
        super(sqsAsyncClientProvider, queueResolver, annotationParser, decoratingMessageProcessorFactory, argumentResolverService);
    }

    @Override
    protected Class<FifoQueueListener> getAnnotationClass() {
        return FifoQueueListener.class;
    }

    @Override
    protected MessageListenerContainer buildContainer(
        final String identifier,
        final SqsAsyncClient sqsAsyncClient,
        final QueueProperties queueProperties,
        final FifoMessageListenerContainerProperties containerProperties,
        final Supplier<MessageProcessor> messageProcessorSupplier
    ) {
        return new FifoMessageListenerContainer(identifier, queueProperties, sqsAsyncClient, messageProcessorSupplier, containerProperties);
    }

    @Override
    protected String getIdentifier(FifoQueueListener annotation) {
        return annotation.identifier();
    }

    @Override
    protected String getQueueNameOrUrl(FifoQueueListener annotation) {
        return annotation.value();
    }

    @Override
    protected String getSqsClientIdentifier(FifoQueueListener annotation) {
        return annotation.sqsClient();
    }
}
