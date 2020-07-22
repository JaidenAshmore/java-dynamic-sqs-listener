package com.jashmore.sqs.container;

import com.jashmore.sqs.retriever.MessageRetriever;
import lombok.experimental.UtilityClass;

import java.time.Duration;

@UtilityClass
class CoreMessageListenerContainerConstants {
    /**
     * The default time that the container will wait for a component to shutdown before giving up.
     */
    static final Duration DEFAULT_SHUTDOWN_TIME = Duration.ofSeconds(60);

    /**
     * The default setting for whether the current messages being processed should be interrupted when the container is being shut down.
     */
    static boolean DEFAULT_SHOULD_INTERRUPT_MESSAGE_PROCESSING_ON_SHUTDOWN = false;

    /**
     * The default setting for whether any extra messages downloaded by the {@link MessageRetriever} should be processed before the container is
     * completely shut down.
     */
    static boolean DEFAULT_SHOULD_PROCESS_EXTRA_MESSAGES_ON_SHUTDOWN = true;
}
