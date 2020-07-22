package com.jashmore.sqs.broker.concurrent;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.util.Preconditions;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Duration;

/**
 * Implementation that stores the value as non-mutable field values and therefore will return the same value on every call.
 *
 * <p>This is useful when you don't need the listener to be dynamic or have the ability to turn off when needed.
 *
 * <p>This implementation is thread safe, even though it doesn't need to be, due to it only returning immutable values.
 */
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
@ThreadSafe
public final class StaticConcurrentMessageBrokerProperties implements ConcurrentMessageBrokerProperties {
    private final Integer concurrencyLevel;
    private final Duration preferredConcurrencyPollingRate;
    private final Duration errorBackoffTime;

    public StaticConcurrentMessageBrokerProperties(final Integer concurrencyLevel,
                                                   final Duration preferredConcurrencyPollingRate,
                                                   final Duration errorBackoffTime) {
        Preconditions.checkNotNull(concurrencyLevel, "concurrencyLevel should not be null");
        Preconditions.checkPositiveOrZero(concurrencyLevel, "concurrencyLevel should be greater than or equal to zero");

        this.concurrencyLevel = concurrencyLevel;
        this.preferredConcurrencyPollingRate = preferredConcurrencyPollingRate;
        this.errorBackoffTime = errorBackoffTime;
    }

    @PositiveOrZero
    @Override
    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Duration getConcurrencyPollingRate() {
        return preferredConcurrencyPollingRate;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Duration getErrorBackoffTime() {
        return errorBackoffTime;
    }
}
