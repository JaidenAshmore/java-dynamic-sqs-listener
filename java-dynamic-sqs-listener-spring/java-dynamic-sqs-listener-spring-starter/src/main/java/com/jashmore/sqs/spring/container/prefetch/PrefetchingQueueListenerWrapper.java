package com.jashmore.sqs.spring.container.prefetch;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.individual.IndividualMessageResolver;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.AbstractQueueAnnotationWrapper;
import com.jashmore.sqs.spring.QueueWrapper;
import com.jashmore.sqs.spring.container.MessageListenerContainer;
import com.jashmore.sqs.spring.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.spring.queue.QueueResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link QueueWrapper} that will wrap methods annotated with {@link PrefetchingQueueListener @PrefetchingQueueListener} with some predefined
 * implementations of the framework.
 */
@Slf4j
@RequiredArgsConstructor
public class PrefetchingQueueListenerWrapper extends AbstractQueueAnnotationWrapper<PrefetchingQueueListener> {
    private final ArgumentResolverService argumentResolverService;
    private final SqsAsyncClient sqsAsyncClient;
    private final QueueResolverService queueResolverService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    protected Class<PrefetchingQueueListener> getAnnotationClass() {
        return PrefetchingQueueListener.class;
    }

    @Override
    protected MessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method, final PrefetchingQueueListener annotation) {
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(queueResolverService.resolveQueueUrl(annotation.value()))
                .build();

        final StaticPrefetchingMessageRetrieverProperties batchingProperties = StaticPrefetchingMessageRetrieverProperties
                .builder()
                .desiredMinPrefetchedMessages(annotation.desiredMinPrefetchedMessages())
                .maxPrefetchedMessages(annotation.maxPrefetchedMessages())
                .visibilityTimeoutForMessagesInSeconds(annotation.messageVisibilityTimeoutInSeconds())
                .maxWaitTimeInSecondsToObtainMessagesFromServer(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .build();
        final PrefetchingMessageRetriever messageRetriever = new PrefetchingMessageRetriever(
                sqsAsyncClient, queueProperties, batchingProperties, executor);

        final MessageResolver messageResolver = new IndividualMessageResolver(queueProperties, sqsAsyncClient);

        final MessageProcessor messageProcessor = new DefaultMessageProcessor(argumentResolverService, queueProperties,
                messageResolver, method, bean);

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

        return new SimpleMessageListenerContainer(identifier, messageRetriever, messageBroker, messageResolver);
    }
}
