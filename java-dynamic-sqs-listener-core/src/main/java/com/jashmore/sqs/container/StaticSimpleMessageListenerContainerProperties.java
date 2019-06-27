package com.jashmore.sqs.container;

import lombok.Builder;
import lombok.Value;

import java.util.concurrent.TimeUnit;

@Value
@Builder
public class StaticSimpleMessageListenerContainerProperties implements SimpleMessageListenerContainerProperties {
    private final long shutdownTimeout;
    private final TimeUnit shutdownTimeUnit;
    private final int shutdownRetryLimit;

    @Override
    public long getShutdownTimeout() {
        return shutdownTimeout;
    }

    @Override
    public TimeUnit getShutdownTimeUnit() {
        return shutdownTimeUnit;
    }

    @Override
    public int getShutdownRetryLimit() {
        return shutdownRetryLimit;
    }
}
