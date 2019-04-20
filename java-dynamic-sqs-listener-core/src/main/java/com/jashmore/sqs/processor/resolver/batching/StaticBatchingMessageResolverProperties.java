package com.jashmore.sqs.processor.resolver.batching;

import lombok.Builder;

import javax.validation.constraints.Max;
import javax.validation.constraints.Positive;

/**
 * Static implementation that will contain constant size and time limit for the buffer.
 */
@Builder
public class StaticBatchingMessageResolverProperties implements BatchingMessageResolverProperties {
    private final long bufferingTimeInMs;
    private final int bufferingSizeLimit;

    @Positive
    @Override
    public long getBufferingTimeInMs() {
        return bufferingTimeInMs;
    }

    @Positive
    @Max(10)
    @Override
    public int getBufferingSizeLimit() {
        return bufferingSizeLimit;
    }
}
