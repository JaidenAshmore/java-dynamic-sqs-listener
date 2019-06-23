package com.jashmore.sqs.retriever.individual;

import lombok.AllArgsConstructor;
import lombok.Builder;

import javax.annotation.Nullable;
import javax.validation.constraints.PositiveOrZero;

@AllArgsConstructor
@Builder(toBuilder = true)
public class StaticIndividualMessageRetrieverProperties implements IndividualMessageRetrieverProperties {
    private final Integer visibilityTimeoutForMessagesInSeconds;
    private final Long errorBackoffTimeInMilliseconds;

    @Override
    public Integer getMessageVisibilityTimeoutInSeconds() {
        return visibilityTimeoutForMessagesInSeconds;
    }

    @Nullable
    @Override
    public @PositiveOrZero Long getErrorBackoffTimeInMilliseconds() {
        return errorBackoffTimeInMilliseconds;
    }
}
