package com.jashmore.sqs.container;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.PositiveOrZero;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class StaticCoreMessageListenerContainerProperties implements CoreMessageListenerContainerProperties {
    String messageProcessingThreadNameFormat;
    Boolean shouldProcessAnyExtraRetrievedMessagesOnShutdown;
    Boolean shouldInterruptThreadsProcessingMessagesOnShutdown;
    Integer messageProcessingShutdownTimeoutInSeconds;
    Integer messageRetrieverShutdownTimeoutInSeconds;
    Integer messageResolverShutdownTimeoutInSeconds;
    Integer messageBrokerShutdownTimeoutInSeconds;

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
    public Integer getMessageBrokerShutdownTimeoutInSeconds() {
        return messageBrokerShutdownTimeoutInSeconds;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Integer getMessageProcessingShutdownTimeoutInSeconds() {
        return messageProcessingShutdownTimeoutInSeconds;
    }

    @Nullable
    @Override
    public Integer getMessageRetrieverShutdownTimeoutInSeconds() {
        return messageRetrieverShutdownTimeoutInSeconds;
    }

    @Nullable
    @Override
    public Integer getMessageResolverShutdownTimeoutInSeconds() {
        return messageResolverShutdownTimeoutInSeconds;
    }
}
