package com.jashmore.sqs.broker.concurrent.properties;

import com.jashmore.sqs.util.Suppliers;

import java.util.function.Supplier;
import javax.validation.constraints.Min;

/**
 * Implementation that will cache the values as the methods to retrieve the values may be costly.
 *
 * <p>For example, an outbound call is needed to get this value and it is costly to do this every time a message has been processed.
 */
public class CachingConcurrentMessageBrokerProperties implements ConcurrentMessageBrokerProperties {
    private Supplier<Integer> cachedConcurrencyLevel;
    private Supplier<Integer> cachedPreferredConcurrencyPollingRateInSeconds;

    public CachingConcurrentMessageBrokerProperties(final int cachingTimeoutInMs,
                                                    final ConcurrentMessageBrokerProperties delegateProperties) {
        this.cachedConcurrencyLevel = Suppliers.memoize(cachingTimeoutInMs, delegateProperties::getConcurrencyLevel);
        this.cachedPreferredConcurrencyPollingRateInSeconds = Suppliers.memoize(cachingTimeoutInMs,
                delegateProperties::getPreferredConcurrencyPollingRateInMilliseconds);
    }

    @Override
    public Integer getConcurrencyLevel() {
        return cachedConcurrencyLevel.get();
    }

    @Override
    public @Min(0) Integer getPreferredConcurrencyPollingRateInMilliseconds() {
        return cachedPreferredConcurrencyPollingRateInSeconds.get();
    }
}
