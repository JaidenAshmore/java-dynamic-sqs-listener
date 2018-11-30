package com.jashmore.sqs.processor.retryable;

import com.google.common.base.Preconditions;

import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.Min;

@Value
@Builder
public class RetryableMessageProcessorProperties {
    /**
     * The number of times that the message should be attempted to be processed.
     *
     * <p>Once it has hit this number and is still failing the exception will be bubbled out to the container.
     */
    @Min(0)
    private final int retryAttempts;
    /**
     * The amount of time between retry attempts in milliseconds.
     */
    @Min(0)
    private final int retryDelayInMs;

    public RetryableMessageProcessorProperties(final int retryAttempts, final int retryDelayInMs) {
        Preconditions.checkArgument(retryAttempts >= 0, "retryAttempt must be greater than 0");
        Preconditions.checkArgument(retryDelayInMs >= 0, "retryDelayInMs must be greater than 0");

        this.retryAttempts = retryAttempts;
        this.retryDelayInMs = retryDelayInMs;
    }
}
