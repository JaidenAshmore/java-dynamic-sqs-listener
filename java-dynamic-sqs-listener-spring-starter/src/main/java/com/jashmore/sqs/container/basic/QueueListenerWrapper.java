package com.jashmore.sqs.container.basic;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;

import com.jashmore.sqs.AbstractQueueAnnotationWrapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.QueueWrapper;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.queue.QueueResolverService;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link QueueWrapper} that will wrap methods annotated with {@link QueueListener @QueueListener} with some predefined
 * implementations of the framework.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueListenerWrapper extends AbstractQueueAnnotationWrapper<QueueListener> {
    private final ArgumentResolverService argumentResolverService;
    private final SqsAsyncClient sqsAsyncClient;
    private final QueueResolverService queueResolverService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    protected Class<QueueListener> getAnnotationClass() {
        return QueueListener.class;
    }

    @Override
    protected MessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method, final QueueListener annotation) {
        final QueueProperties queueProperties;
        try {
            queueProperties = QueueProperties
                    .builder()
                    .queueUrl(queueResolverService.resolveQueueUrl(annotation.value()))
                    .build();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while setting up container");
        }

        final PrefetchingProperties batchingProperties = PrefetchingProperties
                .builder()
                .desiredMinPrefetchedMessages(annotation.desiredMinPrefetchedMessages())
                .maxPrefetchedMessages(annotation.maxPrefetchedMessages())
                .visibilityTimeoutForMessagesInSeconds(annotation.messageVisibilityTimeoutInSeconds())
                .maxWaitTimeInSecondsToObtainMessagesFromServer(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .build();
        final PrefetchingMessageRetriever messageRetriever = new PrefetchingMessageRetriever(
                sqsAsyncClient, queueProperties, batchingProperties, executor);

        final MessageProcessor messageProcessor = new DefaultMessageProcessor(argumentResolverService, queueProperties,
                sqsAsyncClient, method, bean);

        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                executor,
                StaticConcurrentMessageBrokerProperties
                        .builder()
                        .concurrencyLevel(annotation.concurrencyLevel())
                        .build()
        );

        final String identifier;
        if (StringUtils.isEmpty(annotation.identifier().trim())) {
            identifier = bean.getClass().getName() + "#" + method.getName();
        } else {
            identifier = annotation.identifier().trim();
        }

        return new SimpleMessageListenerContainer(identifier, messageRetriever, messageBroker);
    }
}
