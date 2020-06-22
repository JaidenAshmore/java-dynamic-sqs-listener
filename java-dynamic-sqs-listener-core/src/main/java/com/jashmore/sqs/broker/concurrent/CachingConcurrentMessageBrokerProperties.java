package com.jashmore.sqs.broker.concurrent;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import net.jcip.annotations.ThreadSafe;

import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.validation.constraints.PositiveOrZero;

/**
 * Implementation that will provided {@link ConcurrentMessageBrokerProperties} via a cache to reduce the amount of time that it is calculated.
 *
 * <p>This is useful if it is costly to determine the values, e.g. by making an outbound call to determine the value, and therefore a cached value should
 * be used instead.
 */
@ThreadSafe
public class CachingConcurrentMessageBrokerProperties implements ConcurrentMessageBrokerProperties {
    /**
     * Cache key as only a single value is being loaded into the cache.
     */
    private static final Boolean SINGLE_CACHE_VALUE_KEY = true;

    private final LoadingCache<Boolean, Integer> cachedConcurrencyLevel;
    private final LoadingCache<Boolean, Long> cachedPreferredConcurrencyPollingRateInSeconds;
    private final LoadingCache<Boolean, Long> cachedErrorBackoffTimeInMilliseconds;

    /**
     * Constructor.
     *
     * @param cachingTimeoutInMs the amount of time in milliseconds that the values for each property should be cached internally
     * @param delegateProperties the delegate properties object that should be called when the cache has not been populated yet or has expired
     */
    public CachingConcurrentMessageBrokerProperties(final int cachingTimeoutInMs,
                                                    final ConcurrentMessageBrokerProperties delegateProperties) {
        this.cachedConcurrencyLevel = CacheBuilder.newBuilder()
                .expireAfterWrite(cachingTimeoutInMs, TimeUnit.MILLISECONDS)
                .build(CacheLoader.from(delegateProperties::getConcurrencyLevel));

        this.cachedPreferredConcurrencyPollingRateInSeconds = CacheBuilder.newBuilder()
                .expireAfterWrite(cachingTimeoutInMs, TimeUnit.MILLISECONDS)
                .build(CacheLoader.from(delegateProperties::getConcurrencyPollingRateInMilliseconds));

        this.cachedErrorBackoffTimeInMilliseconds = CacheBuilder.newBuilder()
                .expireAfterWrite(cachingTimeoutInMs, TimeUnit.MILLISECONDS)
                .build(CacheLoader.from(delegateProperties::getErrorBackoffTimeInMilliseconds));
    }

    @PositiveOrZero
    @Override
    public int getConcurrencyLevel() {
        return cachedConcurrencyLevel.getUnchecked(SINGLE_CACHE_VALUE_KEY);
    }

    @PositiveOrZero
    @Override
    public Long getConcurrencyPollingRateInMilliseconds() {
        return cachedPreferredConcurrencyPollingRateInSeconds.getUnchecked(SINGLE_CACHE_VALUE_KEY);
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Long getErrorBackoffTimeInMilliseconds() {
        return cachedErrorBackoffTimeInMilliseconds.getUnchecked(SINGLE_CACHE_VALUE_KEY);
    }
}
