package com.jashmore.sqs.broker.concurrent;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import net.jcip.annotations.ThreadSafe;

import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.PositiveOrZero;

/**
 * Implementation that will cache the values as the methods to retrieve the values may be costly.
 *
 * <p>For example, an outbound call is needed to get this value and it is costly to do this every time a message has been processed.
 *
 * <p>This implementation is thread safe even though it is not required to be due to the thread safety of the {@link LoadingCache}.
 */
@ThreadSafe
public class CachingConcurrentMessageBrokerProperties implements ConcurrentMessageBrokerProperties {
    /**
     * Cache key as only a single value is being loaded into the cache.
     */
    private static final int SINGLE_CACHE_VALUE_KEY = 0;

    private final LoadingCache<Integer, Integer> cachedConcurrencyLevel;
    private final LoadingCache<Integer, Long> cachedPreferredConcurrencyPollingRateInSeconds;
    private final LoadingCache<Integer, Long> cachedErrorBackoffTimeInMilliseconds;
    private final String threadNameFormat;

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
                .build(CacheLoader.from(delegateProperties::getPreferredConcurrencyPollingRateInMilliseconds));

        this.cachedErrorBackoffTimeInMilliseconds = CacheBuilder.newBuilder()
                .expireAfterWrite(cachingTimeoutInMs, TimeUnit.MILLISECONDS)
                .build(CacheLoader.from(delegateProperties::getErrorBackoffTimeInMilliseconds));

        this.threadNameFormat = delegateProperties.getThreadNameFormat();
    }

    @Override
    public @Min(0) Integer getConcurrencyLevel() {
        return cachedConcurrencyLevel.getUnchecked(SINGLE_CACHE_VALUE_KEY);
    }

    @Override
    public @Min(0) Long getPreferredConcurrencyPollingRateInMilliseconds() {
        return cachedPreferredConcurrencyPollingRateInSeconds.getUnchecked(SINGLE_CACHE_VALUE_KEY);
    }

    @Override
    public String getThreadNameFormat() {
        return threadNameFormat;
    }

    @Nullable
    @Override
    public @PositiveOrZero Long getErrorBackoffTimeInMilliseconds() {
        return cachedErrorBackoffTimeInMilliseconds.getUnchecked(SINGLE_CACHE_VALUE_KEY);
    }
}
