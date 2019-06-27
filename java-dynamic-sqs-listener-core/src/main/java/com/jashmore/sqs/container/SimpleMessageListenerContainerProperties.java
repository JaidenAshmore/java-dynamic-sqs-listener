package com.jashmore.sqs.container;

import java.util.concurrent.TimeUnit;

public interface SimpleMessageListenerContainerProperties {
    /**
     * The default amount of time for the time unit that the {@link java.util.concurrent.ExecutorService} of the
     * {@link SimpleMessageListenerContainer} will wait for the threads to finish.
     *
     * @return the shutdown timeout
     */
    long getShutdownTimeout();

    /**
     * The time unit that the {@link java.util.concurrent.ExecutorService} of the {@link SimpleMessageListenerContainer} will wait for the threads to finish.
     *
     * @return the shutdown timeout time unit
     */
    TimeUnit getShutdownTimeUnit();

    /**
     * The default amount of times that the container will retry to stop the background threads.
     *
     * @return the shutdown retry limit
     */
    int getShutdownRetryLimit();
}
