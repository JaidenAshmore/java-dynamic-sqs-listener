package com.jashmore.sqs.container.basic;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;

import com.google.common.annotations.VisibleForTesting;

import com.amazonaws.services.sqs.AmazonSQSAsync;
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
import com.jashmore.sqs.queue.QueueResolver;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link QueueWrapper} that will wrap methods annotated with {@link QueueListener @QueueListener} with some predefined
 * implementations of the framework.
 */
@Slf4j
@Service
public class QueueListenerWrapper extends AbstractQueueAnnotationWrapper<QueueListener> {
    private final ArgumentResolverService argumentResolverService;
    private final AmazonSQSAsync amazonSqsAsync;
    private final QueueResolver queueResolver;
    private final ExecutorService executor;

    @Autowired
    public QueueListenerWrapper(final ArgumentResolverService argumentResolverService,
                                final AmazonSQSAsync amazonSqsAsync,
                                final QueueResolver queueResolver) {
        this.argumentResolverService = argumentResolverService;
        this.amazonSqsAsync = amazonSqsAsync;
        this.queueResolver = queueResolver;
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    protected Class<QueueListener> getAnnotationClass() {
        return QueueListener.class;
    }

    @Override
    protected MessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method, final QueueListener annotation) {
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(queueResolver.resolveQueueUrl(annotation.value()))
                .build();

        final PrefetchingProperties batchingProperties = PrefetchingProperties
                .builder()
                .desiredMinPrefetchedMessages(annotation.desiredMinPrefetchedMessages())
                .maxPrefetchedMessages(annotation.maxPrefetchedMessages())
                // I should test here with a visibility timeout already in the queue, does this require one in my retriever? Maybe this shouldn't be a required
                // field as part of this properties
                .visibilityTimeoutForMessagesInSeconds(annotation.messageVisibilityTimeoutInSeconds())
                // Hard code this
                .maxWaitTimeInSecondsToObtainMessagesFromServer(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .build();
        final PrefetchingMessageRetriever messageRetriever = new PrefetchingMessageRetriever(
                amazonSqsAsync, queueProperties, batchingProperties, executor);

        final MessageProcessor messageProcessor = new DefaultMessageProcessor(argumentResolverService, queueProperties,
                amazonSqsAsync, method, bean);

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
