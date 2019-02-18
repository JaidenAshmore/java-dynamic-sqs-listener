package com.jashmore.sqs.retriever.batching;

import lombok.Builder;

import javax.annotation.Nullable;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

/**
 * Static implementation of the properties that will never change during the processing of the messages.
 */
@Builder(toBuilder = true)
public class StaticBatchingMessageRetrieverProperties implements BatchingMessageRetrieverProperties {
    private final int numberOfThreadsWaitingTrigger;
    private final Integer messageRetrievalPollingPeriodInMs;
    private final int visibilityTimeoutInSeconds;
    private final Integer waitTimeInSeconds;

    @Positive
    @Override
    public int getNumberOfThreadsWaitingTrigger() {
        return numberOfThreadsWaitingTrigger;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Integer getMessageRetrievalPollingPeriodInMs() {
        return messageRetrievalPollingPeriodInMs;
    }

    @Positive
    @Override
    public int getVisibilityTimeoutInSeconds() {
        return visibilityTimeoutInSeconds;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Integer getMessageWaitTimeInSeconds() {
        return waitTimeInSeconds;
    }
}
