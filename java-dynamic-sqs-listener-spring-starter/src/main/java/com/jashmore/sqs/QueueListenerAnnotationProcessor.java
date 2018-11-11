package com.jashmore.sqs;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.jashmore.sqs.annotation.QueueListener;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Slf4j
public class QueueListenerAnnotationProcessor implements QueueAnnotationProcessor {
    private final ArgumentResolverService argumentResolverService;
    private final AmazonSQSAsync amazonSqsAsync;
    private final ExecutorService executor;

    public QueueListenerAnnotationProcessor(
            final ArgumentResolverService argumentResolverService,
            final AmazonSQSAsync amazonSqsAsync,
            final ExecutorService executor) {
        this.argumentResolverService = argumentResolverService;
        this.amazonSqsAsync = amazonSqsAsync;
        this.executor = executor;
    }

    @Override
    public boolean canHandleMethod(final Method method) {
        return method.getAnnotation(QueueListener.class) != null;
    }

    @Override
    public MessageListenerContainer wrapMethod(final Object bean, final Method method) {
        final String queueNameOrUrl = method.getAnnotation(QueueListener.class).value();
        // We need to resolve these variables from the spring properties

        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(getQueueUrl(queueNameOrUrl))
                .build();

        final PrefetchingProperties batchingProperties = PrefetchingProperties
                .builder()
                .desiredMinPrefetchedMessages(10)
                .maxPrefetchedMessages(20)
                .maxNumberOfMessagesToObtainFromServer(10)
                .visibilityTimeoutForMessagesInSeconds(10)
                .maxWaitTimeInSecondsToObtainMessagesFromServer(10)
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
                        .concurrencyLevel(10)
                        .preferredConcurrencyPollingRateInMilliseconds(1000)
                        .build()
        );

        return new SimpleMessageListenerContainer(messageRetriever, messageBroker);
    }

    private String getQueueUrl(final String queueNameOrUrl) {
        if (queueNameOrUrl.startsWith("http")) {
            return queueNameOrUrl;
        }

        return amazonSqsAsync.getQueueUrl(queueNameOrUrl).getQueueUrl();
    }

    @AllArgsConstructor
    private static class SimpleMessageListenerContainer implements MessageListenerContainer {
        private final MessageRetriever messageRetriever;
        private final MessageBroker messageBroker;

        @Override
        public void start() {
            if (messageRetriever instanceof AsyncMessageRetriever) {
                ((AsyncMessageRetriever) messageRetriever).start();
            }

            messageBroker.start();
        }

        @Override
        public Future<?> stop() {
            final Future<?> retrieverStoppedFuture;
            if (messageRetriever instanceof AsyncMessageRetriever) {
                retrieverStoppedFuture = ((AsyncMessageRetriever) messageRetriever).stop();
            } else {
                retrieverStoppedFuture = CompletableFuture.completedFuture(null);
            }

            final Future<?> messageBrokerStoppedFuture = messageBroker.stop();

            return CompletableFuture.runAsync(() -> {
                try {
                    retrieverStoppedFuture.get();
                    messageBrokerStoppedFuture.get();
                } catch (final InterruptedException | ExecutionException exception) {
                    log.error("Error waiting for container to stop", exception);
                }
            });
        }
    }
}
