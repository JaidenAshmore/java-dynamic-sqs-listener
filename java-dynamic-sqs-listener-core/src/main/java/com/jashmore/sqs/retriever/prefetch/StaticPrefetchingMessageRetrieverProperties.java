package com.jashmore.sqs.retriever.prefetch;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class StaticPrefetchingMessageRetrieverProperties implements PrefetchingMessageRetrieverProperties {
    @NonNull
    private final Integer desiredMinPrefetchedMessages;
    @NonNull
    private final Integer maxPrefetchedMessages;
    private final Integer maxWaitTimeInSecondsToObtainMessagesFromServer;
    private final Integer visibilityTimeoutForMessagesInSeconds;
    private final Integer errorBackoffTimeInMilliseconds;

    @Override
    public @Positive @NotNull int getDesiredMinPrefetchedMessages() {
        return desiredMinPrefetchedMessages;
    }

    @Override
    public @Min(1) @NotNull int getMaxPrefetchedMessages() {
        return maxPrefetchedMessages;
    }

    @Override
    public @Size(max = MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS) Integer getMessageWaitTimeInSeconds() {
        return maxWaitTimeInSecondsToObtainMessagesFromServer;
    }

    @Override
    public Integer getVisibilityTimeoutForMessagesInSeconds() {
        return visibilityTimeoutForMessagesInSeconds;
    }

    @Override
    public Integer getErrorBackoffTimeInMilliseconds() {
        return errorBackoffTimeInMilliseconds;
    }
}
