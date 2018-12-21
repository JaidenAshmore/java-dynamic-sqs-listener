package com.jashmore.sqs.broker.concurrent.properties;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.Min;

/**
 * Implementation that will cache the values as the methods to retrieve the values may be costly.
 *
 * <p>For example, an outbound call is needed to get this value and it is costly to do this every time a message has been processed.
 */
public class CachingConcurrentMessageBrokerProperties implements ConcurrentMessageBrokerProperties {
    /**
     * Cache key as only a single value is being loaded into the cache.
     */
    private static final Integer SINGLE_CACHE_VALUE_KEY = 0;

    private final LoadingCache<Integer, Integer> cachedConcurrencyLevel;
    private final LoadingCache<Integer, Integer> cachedPreferredConcurrencyPollingRateInSeconds;

    public CachingConcurrentMessageBrokerProperties(final int cachingTimeoutInMs,
                                                    final ConcurrentMessageBrokerProperties delegateProperties) {
        this.cachedConcurrencyLevel = CacheBuilder.newBuilder()
                .expireAfterWrite(cachingTimeoutInMs, TimeUnit.MILLISECONDS)
                .build(CacheLoader.from(delegateProperties::getConcurrencyLevel));

        this.cachedPreferredConcurrencyPollingRateInSeconds = CacheBuilder.newBuilder()
                .expireAfterWrite(cachingTimeoutInMs, TimeUnit.MILLISECONDS)
                .build(CacheLoader.from(delegateProperties::getPreferredConcurrencyPollingRateInMilliseconds));
    }

    @Override
    public @Min(0) Integer getConcurrencyLevel() {
        try {
            return cachedConcurrencyLevel.get(SINGLE_CACHE_VALUE_KEY);
        } catch (ExecutionException executionException) {
            throw new RuntimeException(executionException);
        }
    }

    @Override
    public @Min(0) Integer getPreferredConcurrencyPollingRateInMilliseconds() {
        try {
            return cachedPreferredConcurrencyPollingRateInSeconds.get(SINGLE_CACHE_VALUE_KEY);
        } catch (ExecutionException executionException) {
            throw new RuntimeException(executionException);
        }
    }
}
