package com.jashmore.sqs.container.prefetching;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.StaticCoreMessageListenerContainerProperties;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Container that will prefetch messages and process them concurrently.
 *
 * @see PrefetchingMessageListenerContainerProperties for configuration options
 */
public class PrefetchingMessageListenerContainer implements MessageListenerContainer {
    private final MessageListenerContainer delegate;

    public PrefetchingMessageListenerContainer(
        final String identifier,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient,
        final Supplier<MessageProcessor> messageProcessorSupplier,
        final PrefetchingMessageListenerContainerProperties properties
    ) {
        delegate =
            new CoreMessageListenerContainer(
                identifier,
                buildMessageBrokerSupplier(properties),
                buildMessageRetrieverSupplier(properties, queueProperties, sqsAsyncClient),
                messageProcessorSupplier,
                buildMessageResolverSupplier(queueProperties, sqsAsyncClient),
                StaticCoreMessageListenerContainerProperties
                    .builder()
                    .shouldProcessAnyExtraRetrievedMessagesOnShutdown(properties.processAnyExtraRetrievedMessagesOnShutdown())
                    .shouldInterruptThreadsProcessingMessagesOnShutdown(properties.interruptThreadsProcessingMessagesOnShutdown())
                    .build()
            );
    }

    private Supplier<MessageBroker> buildMessageBrokerSupplier(final PrefetchingMessageListenerContainerProperties properties) {
        return () ->
            new ConcurrentMessageBroker(
                new ConcurrentMessageBrokerProperties() {

                    @PositiveOrZero
                    @Override
                    public int getConcurrencyLevel() {
                        return properties.concurrencyLevel();
                    }

                    @Nullable
                    @Positive
                    @Override
                    public Duration getConcurrencyPollingRate() {
                        return properties.concurrencyPollingRate();
                    }

                    @Nullable
                    @PositiveOrZero
                    @Override
                    public Duration getErrorBackoffTime() {
                        return properties.errorBackoffTime();
                    }
                }
            );
    }

    private Supplier<MessageRetriever> buildMessageRetrieverSupplier(
        final PrefetchingMessageListenerContainerProperties properties,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient
    ) {
        return () ->
            new PrefetchingMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                new PrefetchingMessageRetrieverProperties() {

                    @Positive
                    @Override
                    public int getDesiredMinPrefetchedMessages() {
                        return properties.desiredMinPrefetchedMessages();
                    }

                    @Override
                    public @Positive int getMaxPrefetchedMessages() {
                        return properties.maxPrefetchedMessages();
                    }

                    @Nullable
                    @Positive
                    @Override
                    public Duration getMessageVisibilityTimeout() {
                        return properties.messageVisibilityTimeout();
                    }

                    @Nullable
                    @PositiveOrZero
                    @Override
                    public Duration getErrorBackoffTime() {
                        return properties.errorBackoffTime();
                    }
                }
            );
    }

    private Supplier<MessageResolver> buildMessageResolverSupplier(
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient
    ) {
        return () -> new BatchingMessageResolver(queueProperties, sqsAsyncClient);
    }

    @Override
    public String getIdentifier() {
        return delegate.getIdentifier();
    }

    @Override
    public CompletableFuture<?> start() {
        return delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void stop(final Duration duration) {
        delegate.stop(duration);
    }
}
