package com.jashmore.sqs.broker.concurrent.properties;

import javax.validation.constraints.Min;

public interface ConcurrentMessageBrokerProperties {
    /**
     * The level of concurrency for processing messages, e.g. the number of threads processing messages.
     */
    Integer getConcurrencyLevel();

    /**
     * The number of milliseconds between checks for the rate of concurrency.
     *
     * <p>For example, if the concurrency is currently set to 0 (therefore no threads running) it will poll at this rate to see
     * if the concurrency has increased.
     *
     * <p>The higher this number the less CPU intensive the checking is as it is cycling less. The lower this number however the more responsive the application
     * is to changing concurrency level.
     *
     * @return the number of milliseconds between polls for the concurrency level
     */
    @Min(0)
    Integer getPreferredConcurrencyPollingRateInMilliseconds();
}
