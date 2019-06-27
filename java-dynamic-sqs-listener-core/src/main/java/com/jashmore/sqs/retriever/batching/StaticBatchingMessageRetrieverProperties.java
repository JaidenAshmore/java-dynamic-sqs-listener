package com.jashmore.sqs.retriever.batching;

import lombok.Builder;
import lombok.Value;

import javax.annotation.Nullable;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

/**
 * Static implementation of the properties that will never change during the processing of the messages.
 */
@Value
@Builder(toBuilder = true)
public class StaticBatchingMessageRetrieverProperties implements BatchingMessageRetrieverProperties {
    private final int numberOfThreadsWaitingTrigger;
    private final Long messageRetrievalPollingPeriodInMs;
    private final Integer messageVisibilityTimeoutInSeconds;
    private final Long errorBackoffTimeInMilliseconds;

    @Positive
    @Override
    public int getNumberOfThreadsWaitingTrigger() {
        return numberOfThreadsWaitingTrigger;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Long getMessageRetrievalPollingPeriodInMs() {
        return messageRetrievalPollingPeriodInMs;
    }

    @Nullable
    @Positive
    @Override
    public Integer getMessageVisibilityTimeoutInSeconds() {
        return messageVisibilityTimeoutInSeconds;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Long getErrorBackoffTimeInMilliseconds() {
        return errorBackoffTimeInMilliseconds;
    }
}
