package com.jashmore.sqs.retriever.individual;

import lombok.experimental.UtilityClass;

@UtilityClass
public class IndividualMessageRetrieverConstants {
    /**
     * The default amount of time to sleep the thread when there was an error obtaining messages.
     */
    public static final int DEFAULT_BACKOFF_TIME_IN_MS = 10_000;
}
