package com.jashmore.sqs.retriever.batching;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import lombok.Builder;
import lombok.Value;

/**
 * Static implementation of the properties that will never change during the processing of the messages.
 */
@Value
@Builder(toBuilder = true)
public class StaticBatchingMessageRetrieverProperties implements BatchingMessageRetrieverProperties {
    int batchSize;
    Long batchingPeriodInMs;
    Integer messageVisibilityTimeoutInSeconds;
    Long errorBackoffTimeInMilliseconds;

    @Positive
    @Override
    public int getBatchSize() {
        return batchSize;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Long getBatchingPeriodInMs() {
        return batchingPeriodInMs;
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
