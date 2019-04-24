package com.jashmore.sqs.spring;

import com.jashmore.sqs.container.MessageListenerContainer;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

/**
 * Defines a {@link MessageListenerContainer} with a provided identifier that can be used by the {@link QueueContainerService} to start and stop them during
 * execution.
 */
@Value
@NonFinal
@Builder
public class IdentifiableMessageListenerContainer {
    /**
     * The unique identifier for this container which should not be the same as any other container.
     *
     * <p>For the default implementations provided by the core Spring Starter the unique identifier is the URL for the queue
     * and therefore it isn't possible two different methods call the same queue.
     */
    private String identifier;
    /**
     * The container that wraps a method and is identifiable by the {@link #identifier}.
     */
    private MessageListenerContainer container;
}
