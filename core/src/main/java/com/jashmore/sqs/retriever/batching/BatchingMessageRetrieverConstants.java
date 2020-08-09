package com.jashmore.sqs.retriever.batching;

import java.time.Duration;
import lombok.experimental.UtilityClass;

@UtilityClass
class BatchingMessageRetrieverConstants {
    /**
     * The default amount of time to sleep the thread when there was an error obtaining messages.
     */
    static final Duration DEFAULT_BACKOFF_TIME = Duration.ofSeconds(10);

    /**
     * The default number of threads requesting messages for it trigger a request for messages.
     */
    static final int DEFAULT_BATCHING_TRIGGER = 1;
}
