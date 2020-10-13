package com.jashmore.sqs.container.batching;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Nonnull;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.container.CoreMessageListenerContainerProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolverProperties;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Container that will request and resolve messages in batches.
 *
 * @see BatchingMessageListenerContainerProperties for configuration options
 */
public class BatchingMessageListenerContainer implements MessageListenerContainer {

    private final MessageListenerContainer delegate;

    public BatchingMessageListenerContainer(
        final String identifier,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient,
        final Supplier<MessageProcessor> messageProcessorSupplier,
        final BatchingMessageListenerContainerProperties properties
    ) {
        this.delegate =
            new CoreMessageListenerContainer(
                identifier,
                buildMessageBrokerSupplier(properties),
                buildMessageRetrieverSupplier(queueProperties, sqsAsyncClient, properties),
                messageProcessorSupplier,
                buildMessageResolver(queueProperties, sqsAsyncClient, properties),
                new CoreMessageListenerContainerProperties() {
                    @Nullable
                    @Override
                    public Boolean shouldInterruptThreadsProcessingMessagesOnShutdown() {
                        return properties.interruptThreadsProcessingMessagesOnShutdown();
                    }

                    @Nullable
                    @Override
                    public Boolean shouldProcessAnyExtraRetrievedMessagesOnShutdown() {
                        return properties.processAnyExtraRetrievedMessagesOnShutdown();
                    }

                    @Nullable
                    @PositiveOrZero
                    @Override
                    public Duration getMessageBrokerShutdownTimeout() {
                        return null;
                    }

                    @Nullable
                    @PositiveOrZero
                    @Override
                    public Duration getMessageRetrieverShutdownTimeout() {
                        return null;
                    }

                    @Nullable
                    @PositiveOrZero
                    @Override
                    public Duration getMessageProcessingShutdownTimeout() {
                        return null;
                    }

                    @Nullable
                    @PositiveOrZero
                    @Override
                    public Duration getMessageResolverShutdownTimeout() {
                        return null;
                    }
                }
            );
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
    public void stop(Duration duration) {
        delegate.stop(duration);
    }

    private Supplier<MessageBroker> buildMessageBrokerSupplier(final BatchingMessageListenerContainerProperties properties) {
        return () ->
            new ConcurrentMessageBroker(
                new ConcurrentMessageBrokerProperties() {
                    @Override
                    public @PositiveOrZero int getConcurrencyLevel() {
                        return properties.concurrencyLevel();
                    }

                    @Override
                    public @Nullable @Positive Duration getConcurrencyPollingRate() {
                        return properties.concurrencyPollingRate();
                    }

                    @Override
                    public @Nullable @PositiveOrZero Duration getErrorBackoffTime() {
                        return properties.errorBackoffTime();
                    }
                }
            );
    }

    private Supplier<MessageRetriever> buildMessageRetrieverSupplier(
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient,
        final BatchingMessageListenerContainerProperties properties
    ) {
        return () ->
            new BatchingMessageRetriever(
                queueProperties,
                sqsAsyncClient,
                new BatchingMessageRetrieverProperties() {
                    @Positive
                    @Max(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS)
                    @Override
                    public int getBatchSize() {
                        return properties.batchSize();
                    }

                    @Nullable
                    @Positive
                    @Override
                    public Duration getBatchingPeriod() {
                        return properties.getBatchingPeriod();
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

    private Supplier<MessageResolver> buildMessageResolver(
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient,
        final BatchingMessageListenerContainerProperties properties
    ) {
        return () ->
            new BatchingMessageResolver(
                queueProperties,
                sqsAsyncClient,
                new BatchingMessageResolverProperties() {
                    @Positive
                    @Max(AwsConstants.MAX_NUMBER_OF_MESSAGES_IN_BATCH)
                    @Override
                    public int getBufferingSizeLimit() {
                        return properties.batchSize();
                    }

                    @Nonnull
                    @Positive
                    @Override
                    public Duration getBufferingTime() {
                        return properties.getBatchingPeriod();
                    }
                }
            );
    }
}
