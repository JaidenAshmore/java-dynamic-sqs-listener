package com.jashmore.sqs.container;

import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;

import javax.annotation.Nullable;
import javax.validation.constraints.PositiveOrZero;

public interface CoreMessageListenerContainerProperties {
    /**
     * Whether the threads that are processing messages should be interrupted during shutdown of the broker.
     *
     * <p>Setting this to true is useful if it may take long to process messages and it is undesirable for them to finish before shutting down. If this
     * value is null, it will default to {@link CoreMessageListenerContainerConstants#DEFAULT_SHOULD_INTERRUPT_MESSAGE_PROCESSING_ON_SHUTDOWN} in
     * the {@link CoreMessageListenerContainer}.
     *
     * @return whether the message processing threads should be interrupted during shutdown
     */
    @Nullable
    Boolean shouldInterruptThreadsProcessingMessagesOnShutdown();

    /**
     * When using an {@link MessageRetriever} there may be extra messages that have been downloaded asynchronously by the {@link MessageRetriever} but
     * not consumed and this will determine whether these messages should be processed before shutting down the broker.
     *
     * <p>Setting this to true will make sure that all of the extra messages are processed before the container is shut down. If this value is null,
     * it will default to {@link CoreMessageListenerContainerConstants#DEFAULT_SHOULD_PROCESS_EXTRA_MESSAGES_ON_SHUTDOWN} in the
     * {@link CoreMessageListenerContainer}.
     *
     * @return whether extra messages should be processed before shut down
     */
    @Nullable
    Boolean shouldProcessAnyExtraRetrievedMessagesOnShutdown();

    /**
     * Gets the amount of time that the broker should wait for the {@link MessageRetriever} to shutdown when the broker is being shutdown.
     *
     * <p>If this value is negative or null, then {@link CoreMessageListenerContainerConstants#DEFAULT_SHUTDOWN_TIME_IN_SECONDS} will be used for instead.
     *
     * @return the number of seconds to wait for the message retriever to shutdown
     */
    @Nullable
    @PositiveOrZero
    Integer getMessageRetrieverShutdownTimeoutInSeconds();

    /**
     * The number of seconds that the broker should wait for the message processing threads to finish when a shutdown is initiated.
     *
     * <p>When {@link #shouldProcessAnyExtraRetrievedMessagesOnShutdown()} is true and there are extra messages to be processed, this field will try and
     * put as many messages onto threads to be processed before this limit is hit. If this time limit is reached some messages may not have been processed.
     *
     * <p>If this value is negative or null, then {@link CoreMessageListenerContainerConstants#DEFAULT_SHUTDOWN_TIME_IN_SECONDS} will be used for instead.
     *
     * @return the time in seconds to wait for shutdown of message processing threads
     */
    @Nullable
    @PositiveOrZero
    Integer getMessageProcessingShutdownTimeoutInSeconds();

    /**
     * Gets the amount of time that the broker should wait for the {@link MessageResolver} to shutdown when the broker is being shutdown.
     *
     * <p>If this value is negative or null, then {@link CoreMessageListenerContainerConstants#DEFAULT_SHUTDOWN_TIME_IN_SECONDS} will be used for instead.
     *
     * @return the number of seconds to wait for the message resolver to shutdown
     */
    @Nullable
    @PositiveOrZero
    Integer getMessageResolverShutdownTimeoutInSeconds();
}
