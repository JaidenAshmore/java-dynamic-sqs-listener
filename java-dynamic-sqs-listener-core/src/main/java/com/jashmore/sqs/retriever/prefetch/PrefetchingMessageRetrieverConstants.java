package com.jashmore.sqs.retriever.prefetch;

import lombok.experimental.UtilityClass;

@UtilityClass
class PrefetchingMessageRetrieverConstants {
    /**
     * The default backoff timeout for when there is an error retrieving messages.
     */
    static final int DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS = 10_000;
}
