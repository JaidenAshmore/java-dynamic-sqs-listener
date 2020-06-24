package com.jashmore.sqs.retriever.prefetch;

import com.jashmore.documentation.annotations.Min;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

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
    @Positive
    public int getDesiredMinPrefetchedMessages() {
        return desiredMinPrefetchedMessages;
    }

    @Override
    @Min(1)
    public int getMaxPrefetchedMessages() {
        return maxPrefetchedMessages;
    }

    @Override
    @Nullable
    @Positive
    public Integer getMessageVisibilityTimeoutInSeconds() {
        return messageVisibilityTimeoutInSeconds;
    }

    @Override
    @Nullable
    @PositiveOrZero
    public Integer getErrorBackoffTimeInMilliseconds() {
        return errorBackoffTimeInMilliseconds;
    }
}
