package com.jashmore.sqs.resolver.batching;

import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_IN_BATCH;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Positive;
import java.time.Duration;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Static implementation that will contain constant size and time limit for the buffer.
 */
@Value
@Builder(toBuilder = true)
public class StaticBatchingMessageResolverProperties implements BatchingMessageResolverProperties {

    int bufferingSizeLimit;
    Duration bufferingTime;

    @Positive
    @Max(MAX_NUMBER_OF_MESSAGES_IN_BATCH)
    @Override
    public int getBufferingSizeLimit() {
        return bufferingSizeLimit;
    }

    @NonNull
    @Positive
    @Override
    public Duration getBufferingTime() {
        return bufferingTime;
    }
}
