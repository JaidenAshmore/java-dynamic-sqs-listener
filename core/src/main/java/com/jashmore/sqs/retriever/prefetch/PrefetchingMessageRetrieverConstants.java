package com.jashmore.sqs.retriever.prefetch;

import java.time.Duration;
import lombok.experimental.UtilityClass;

@UtilityClass
class PrefetchingMessageRetrieverConstants {

    /**
     * The default backoff timeout for when there is an error retrieving messages.
     */
    static final Duration DEFAULT_ERROR_BACKOFF_TIMEOUT = Duration.ofSeconds(10);
}
