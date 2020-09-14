package com.jashmore.sqs.broker.grouping;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import java.time.Duration;
import java.util.function.Function;
import lombok.Builder;
import lombok.Value;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 *
 */
@Value
@Builder(toBuilder = true)
public class StaticGroupingMessageBrokerProperties implements GroupingMessageBrokerProperties {
    int concurrencyLevel;
    Duration concurrencyPollingRate;
    Duration errorBackoffTime;
    int maximumConcurrentMessageRetrieval;
    Function<Message, String> groupingFunction;

    @PositiveOrZero
    @Override
    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    @Nullable
    @Positive
    @Override
    public Duration getConcurrencyPollingRate() {
        return concurrencyPollingRate;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Duration getErrorBackoffTime() {
        return errorBackoffTime;
    }

    @Override
    public int getMaximumConcurrentMessageRetrieval() {
        return maximumConcurrentMessageRetrieval;
    }

    public String groupMessage(final Message message) {
        return groupingFunction.apply(message);
    }
}
