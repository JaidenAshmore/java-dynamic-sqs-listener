package com.jashmore.sqs.container;

import lombok.experimental.UtilityClass;

import java.util.concurrent.TimeUnit;

@UtilityClass
public class SimpleMessageListenerContainerConstants {
    /**
     * The default amount of time for the time unit that the {@link java.util.concurrent.ExecutorService} of the
     * {@link SimpleMessageListenerContainer} will wait for the threads to finish.
     */
    public static final long DEFAULT_SHUTDOWN_TIMEOUT = 20;

    /**
     * The default time unit that the {@link java.util.concurrent.ExecutorService} of the
     * {@link SimpleMessageListenerContainer} will wait for the threads to finish.
     */
    public static final TimeUnit DEFAULT_SHUTDOWN_TIMEOUT_TIME_UNIT = TimeUnit.SECONDS;

    /**
     * The default amount of times that the container will retry to stop the background threads.
     */
    public static final int DEFAULT_SHUTDOWN_RETRY_AMOUNT = 2;
}
