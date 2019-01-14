package com.jashmore.sqs.retriever.batching;

import lombok.Builder;

/**
 * Static implementation of the properties that will never change during the processing of the messages.
 */
@Builder(toBuilder = true)
public class StaticBatchingProperties implements BatchingProperties {
    private final int numberOfThreadsWaitingTrigger;
    private final int messageRetrievalPollingPeriodInMs;
    private final int visibilityTimeoutInSeconds;

    @Override
    public int getNumberOfThreadsWaitingTrigger() {
        return numberOfThreadsWaitingTrigger;
    }

    @Override
    public int getMessageRetrievalPollingPeriodInMs() {
        return messageRetrievalPollingPeriodInMs;
    }

    @Override
    public int getVisibilityTimeoutInSeconds() {
        return visibilityTimeoutInSeconds;
    }
}
