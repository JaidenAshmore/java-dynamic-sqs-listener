package com.jashmore.sqs.container;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.PositiveOrZero;
import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class StaticCoreMessageListenerContainerProperties implements CoreMessageListenerContainerProperties {

    Boolean shouldProcessAnyExtraRetrievedMessagesOnShutdown;
    Boolean shouldInterruptThreadsProcessingMessagesOnShutdown;
    Duration messageProcessingShutdownTimeout;
    Duration messageRetrieverShutdownTimeout;
    Duration messageResolverShutdownTimeout;
    Duration messageBrokerShutdownTimeout;

    @Nullable
    @Override
    public Boolean shouldInterruptThreadsProcessingMessagesOnShutdown() {
        return shouldInterruptThreadsProcessingMessagesOnShutdown;
    }

    @Nullable
    @Override
    public Boolean shouldProcessAnyExtraRetrievedMessagesOnShutdown() {
        return shouldProcessAnyExtraRetrievedMessagesOnShutdown;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Duration getMessageBrokerShutdownTimeout() {
        return messageBrokerShutdownTimeout;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Duration getMessageProcessingShutdownTimeout() {
        return messageProcessingShutdownTimeout;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Duration getMessageRetrieverShutdownTimeout() {
        return messageRetrieverShutdownTimeout;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Duration getMessageResolverShutdownTimeout() {
        return messageResolverShutdownTimeout;
    }
}
