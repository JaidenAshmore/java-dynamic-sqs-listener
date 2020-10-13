package com.jashmore.sqs.retriever.batching;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import java.time.Duration;
import lombok.Builder;
import lombok.Value;

/**
 * Static implementation of the properties that will never change during the processing of the messages.
 */
@Value
@Builder(toBuilder = true)
public class StaticBatchingMessageRetrieverProperties implements BatchingMessageRetrieverProperties {

    int batchSize;
    Duration batchingPeriod;
    Duration messageVisibilityTimeout;
    Duration errorBackoffTime;

    @Positive
    @Override
    public int getBatchSize() {
        return batchSize;
    }

    @Nullable
    @Positive
    @Override
    public Duration getBatchingPeriod() {
        return batchingPeriod;
    }

    @Nullable
    @Positive
    @Override
    public Duration getMessageVisibilityTimeout() {
        return messageVisibilityTimeout;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Duration getErrorBackoffTime() {
        return errorBackoffTime;
    }
}
