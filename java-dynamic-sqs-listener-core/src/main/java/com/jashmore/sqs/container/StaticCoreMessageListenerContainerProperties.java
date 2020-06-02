package com.jashmore.sqs.container;

import lombok.Builder;
import lombok.Value;

import javax.annotation.Nullable;
import javax.validation.constraints.PositiveOrZero;

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
    @Override
    public @PositiveOrZero Integer getMessageBrokerShutdownTimeoutInSeconds() {
        return messageBrokerShutdownTimeoutInSeconds;
    }

    @Nullable
    @Override
    public @PositiveOrZero Integer getMessageProcessingShutdownTimeoutInSeconds() {
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
