package com.jashmore.sqs.retriever.batching;

import lombok.experimental.UtilityClass;

@UtilityClass
public class BatchingMessageRetrieverConstants {
    /**
     * The default amount of time to sleep the thread when there was an error obtaining messages.
     */
    public static final int DEFAULT_BACKOFF_TIME_IN_MS = 10_000;
}
