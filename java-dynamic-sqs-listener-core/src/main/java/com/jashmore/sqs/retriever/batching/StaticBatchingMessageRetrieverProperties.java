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
    private final Integer visibilityTimeoutInSeconds;
    private final Integer waitTimeInSeconds;
    private final Integer errorBackoffTimeInMilliseconds;

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

    @Nullable
    @Positive
    @Override
    public Integer getVisibilityTimeoutInSeconds() {
        return visibilityTimeoutInSeconds;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Integer getMessageWaitTimeInSeconds() {
        return waitTimeInSeconds;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Integer getErrorBackoffTimeInMilliseconds() {
        return errorBackoffTimeInMilliseconds;
    }
}
