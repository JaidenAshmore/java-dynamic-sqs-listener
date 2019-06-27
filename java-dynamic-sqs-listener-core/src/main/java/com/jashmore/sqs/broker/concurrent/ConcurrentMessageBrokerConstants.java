package com.jashmore.sqs.broker.concurrent;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ConcurrentMessageBrokerConstants {
    /**
     * The default amount of time to sleep the thread when there was an error organising the message processing threads.
     */
    public static final int DEFAULT_BACKOFF_TIME_IN_MS = 10_000;

    /**
     * The default amount of time the thread should wait for a thread to process a message before it tries again and checks the available concurrency.
     */
    public static final long DEFAULT_CONCURRENCY_POLLING_IN_MS = 60_000L;

    /**
     * The default time that the broker will wait for background threads to process during a shutdown.
     */
    public static final long DEFAULT_SHUTDOWN_TIME_IN_SECONDS = 60;
}
