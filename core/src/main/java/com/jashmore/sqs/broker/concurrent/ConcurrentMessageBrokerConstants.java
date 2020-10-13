package com.jashmore.sqs.broker.concurrent;

import java.time.Duration;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ConcurrentMessageBrokerConstants {

    /**
     * The default amount of time to sleep the thread when there was an error organising the message processing threads.
     */
    public static final Duration DEFAULT_BACKOFF_TIME = Duration.ofSeconds(10);

    /**
     * The default amount of time the thread should wait for a thread to process a message before it tries again and checks the available concurrency.
     */
    public static final Duration DEFAULT_CONCURRENCY_POLLING = Duration.ofMinutes(1);
}
