package com.jashmore.sqs.spring.container.custom;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.retriever.MessageRetriever;

/**
 * Factory used to build a {@link MessageRetriever} for a {@link CustomQueueListener @ConfigurableQueueListener} annotated method. This allows for
 * consumers of this framework to define a specific {@link MessageRetriever} implementation that they would like for annotated method which is not
 * provided by another annotation.
 */
@FunctionalInterface
public interface MessageRetrieverFactory {
    /**
     * Construct a {@link MessageRetriever} that will be used for retrieving messages from the provided queue.
     *
     * @param queueProperties details about the queue that the message is being processed for
     * @return a {@link MessageRetriever} that will retrieve messages for processing
     */
    MessageRetriever createMessageRetriever(QueueProperties queueProperties);
}
