package com.jashmore.sqs.container.fifo;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.grouping.GroupingMessageBroker;
import com.jashmore.sqs.broker.grouping.GroupingMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.StaticCoreMessageListenerContainerProperties;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

/**
 * Container used to configure listening to a FIFO SQS Queue.
 *
 * <p>A FIFO queue makes sure to process each message in a message group in the order that it was created and therefore two messages in the same message
 * group cannot be processed at the same time. Therefore, this can be used when you have a distinct order for your SQS messages and you want to guarantee
 * that they are processed one after another.
 *
 * <p>Due to this ordering constraint, the throughput of a FIFO queue can be significantly lower than a Standard SQS queue so care is needed to make sure
 * to maintain performance. Some ways to increase performance is making sure that there are a large range of message group IDs as messages in two message
 * groups can be done concurrently.
 *
 * <p>If a FIFO SQS message fails to be processed, the listener will not process any other messages that were downloaded for that message group until the
 * message visibility for that group expires potentially resulting in that message to be placed onto the queue. Note that with the implementation of FIFO
 * queues if the message ends up in the DLQ it will still allow processing of messages in that message group after that message.
 *
 * @see FifoMessageListenerContainerProperties for configuration options
 * @see <a href="https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues.html">AWS SQS FIFO documentation</a> for more details
 *     about FIFO SQS queues
 */
public class FifoMessageListenerContainer implements MessageListenerContainer {

    private final MessageListenerContainer delegate;

    public FifoMessageListenerContainer(
        final String identifier,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient,
        final Supplier<MessageProcessor> messageProcessorSupplier,
        final FifoMessageListenerContainerProperties properties
    ) {
        this.delegate =
            new CoreMessageListenerContainer(
                identifier,
                messageBrokerSupplier(properties),
                messageRetrieverSupplier(queueProperties, sqsAsyncClient, properties),
                messageProcessorSupplier,
                messageResolverSupplier(queueProperties, sqsAsyncClient),
                StaticCoreMessageListenerContainerProperties
                    .builder()
                    .shouldInterruptThreadsProcessingMessagesOnShutdown(properties.interruptThreadsProcessingMessagesOnShutdown())
                    .shouldProcessAnyExtraRetrievedMessagesOnShutdown(false)
                    .build()
            );
    }

    private Supplier<MessageBroker> messageBrokerSupplier(final FifoMessageListenerContainerProperties properties) {
        return () ->
            new GroupingMessageBroker(
                new GroupingMessageBrokerProperties() {
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

                    @Override
                    public int getMaximumNumberOfCachedMessageGroups() {
                        return properties.maximumCachedMessageGroups();
                    }

                    @Override
                    public Function<Message, String> messageGroupingFunction() {
                        return message -> message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
                    }

                    @Override
                    public boolean purgeExtraMessagesInGroupOnError() {
                        return true;
                    }

                    @Override
                    public boolean processCachedMessagesOnShutdown() {
                        return properties.tryAndProcessAnyExtraRetrievedMessagesOnShutdown();
                    }
                }
            );
    }

    private Supplier<MessageRetriever> messageRetrieverSupplier(
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient,
        final FifoMessageListenerContainerProperties properties
    ) {
        return () ->
            new BatchingMessageRetriever(
                queueProperties,
                sqsAsyncClient,
                new BatchingMessageRetrieverProperties() {
                    @Override
                    @Positive
                    @Max(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS)
                    public int getBatchSize() {
                        return properties.maximumMessagesInMessageGroup();
                    }

                    @Override
                    @Nullable
                    @Positive
                    public Duration getBatchingPeriod() {
                        return Duration.ofSeconds(5);
                    }

                    @Override
                    @Nullable
                    @Positive
                    public Duration getMessageVisibilityTimeout() {
                        return properties.messageVisibilityTimeout();
                    }

                    @Override
                    @Nullable
                    @PositiveOrZero
                    public Duration getErrorBackoffTime() {
                        return properties.errorBackoffTime();
                    }
                }
            );
    }

    private Supplier<MessageResolver> messageResolverSupplier(final QueueProperties queueProperties, final SqsAsyncClient sqsAsyncClient) {
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
    public void stop(Duration duration) {
        delegate.stop(duration);
    }
}
