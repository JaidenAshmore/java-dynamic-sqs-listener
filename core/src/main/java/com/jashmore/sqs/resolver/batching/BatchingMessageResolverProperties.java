package com.jashmore.sqs.resolver.batching;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Nonnull;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.sqs.aws.AwsConstants;
import java.time.Duration;

/**
 * Properties used for configuring the {@link BatchingMessageResolver} specifically the size of the buffer that should be used.
 */
public interface BatchingMessageResolverProperties {
    /**
     * The maximum size of the buffer before a batch of message resolution should be triggered.
     *
     * <p>This value must be greater than or equal to zero as it does not make sense to have a buffer of size zero. If the buffer was size one it should resolve
     * the message immediately.  There is a limit on the maximum size of the buffer due to AWS limits on the batch being
     * {@link AwsConstants#MAX_NUMBER_OF_MESSAGES_IN_BATCH}.
     *
     * @return the maximum number of messages that can be buffered before the batch should be submitted for resolution
     */
    @Positive
    @Max(AwsConstants.MAX_NUMBER_OF_MESSAGES_IN_BATCH)
    int getBufferingSizeLimit();

    /**
     * The amount of time that the message should remain in the buffer before a batch is sent.
     *
     * <p>This value must be greater than zero as it does not make sense for it to be negative. It is also recommended not to have this as a
     * very small time duration as a small duration will result in a constant looping of the buffering thread.
     *
     * @return the amount of time that a message can be buffered for resolution
     */
    @Nonnull
    @Positive
    Duration getBufferingTime();
}
