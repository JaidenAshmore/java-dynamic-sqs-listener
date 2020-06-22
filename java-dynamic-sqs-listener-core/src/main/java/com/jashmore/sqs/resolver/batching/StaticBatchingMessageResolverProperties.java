package com.jashmore.sqs.resolver.batching;

import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_IN_BATCH;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Positive;
import lombok.Builder;
import lombok.Value;

/**
 * Static implementation that will contain constant size and time limit for the buffer.
 */
@Value
@Builder(toBuilder = true)
public class StaticBatchingMessageResolverProperties implements BatchingMessageResolverProperties {
    long bufferingTimeInMs;
    int bufferingSizeLimit;

    @Positive
    @Override
    public long getBufferingTimeInMs() {
        return bufferingTimeInMs;
    }

    @Positive
    @Max(MAX_NUMBER_OF_MESSAGES_IN_BATCH)
    @Override
    public int getBufferingSizeLimit() {
        return bufferingSizeLimit;
    }
}
