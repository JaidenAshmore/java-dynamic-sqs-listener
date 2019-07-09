package com.jashmore.sqs.retriever.batching;

import lombok.experimental.UtilityClass;

@UtilityClass
class BatchingMessageRetrieverConstants {
    /**
     * The default amount of time to sleep the thread when there was an error obtaining messages.
     */
    static final int DEFAULT_BACKOFF_TIME_IN_MS = 10_000;

    /**
     * The default number of threads requesting messages for it trigger a request for messages.
     */
    static final int DEFAULT_BATCHING_TRIGGER = 1;

    /**
     * The default number of threads requesting messages for it trigger a request for messages.
     */
    static final long DEFAULT_BATCHING_PERIOD_IN_MS = Long.MAX_VALUE;
}
