package com.jashmore.sqs.annotations.core.fifo;

import com.jashmore.sqs.annotations.container.AnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainer;
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainerProperties;
import com.jashmore.sqs.client.SqsAsyncClientProvider;
import com.jashmore.sqs.container.MessageListenerContainerFactory;
import com.jashmore.sqs.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.client.QueueResolver;

import java.lang.reflect.Method;
import java.util.Optional;

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
public class FifoAnnotationMessageListenerContainerFactory implements MessageListenerContainerFactory {

    private final AnnotationMessageListenerContainerFactory<FifoQueueListener> delegate;

    public FifoAnnotationMessageListenerContainerFactory(
        final ArgumentResolverService argumentResolverService,
        final SqsAsyncClientProvider sqsAsyncClientProvider,
        final QueueResolver queueResolver,
        final FifoQueueListenerParser annotationParser,
        final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
    ) {
        this.delegate = new AnnotationMessageListenerContainerFactory<>(
                FifoQueueListener.class,
                FifoQueueListener::identifier,
                FifoQueueListener::sqsClient,
                FifoQueueListener::value,
                queueResolver,
                sqsAsyncClientProvider,
                decoratingMessageProcessorFactory,
                argumentResolverService,
                (details) -> {
                    final FifoMessageListenerContainerProperties properties = annotationParser.parse(details.annotation);
                    return new FifoMessageListenerContainer(
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
    public Optional<MessageListenerContainer> buildContainer(Object bean, Method method) throws MessageListenerContainerInitialisationException {
        return this.delegate.buildContainer(bean, method);
    }
}
