package com.jashmore.sqs.retriever.prefetch;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class StaticPrefetchingMessageRetrieverProperties implements PrefetchingMessageRetrieverProperties {
    @NonNull
    private final Integer desiredMinPrefetchedMessages;
    @NonNull
    private final Integer maxPrefetchedMessages;
    private final Integer messageVisibilityTimeoutInSeconds;
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
    public Integer getMessageVisibilityTimeoutInSeconds() {
        return messageVisibilityTimeoutInSeconds;
    }

    @Override
    public Integer getErrorBackoffTimeInMilliseconds() {
        return errorBackoffTimeInMilliseconds;
    }
}
