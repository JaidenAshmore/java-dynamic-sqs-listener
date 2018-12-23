package com.jashmore.sqs.container.custom;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;

/**
 * Factory used to build a {@link MessageBroker} for a {@link CustomQueueListener @ConfigurableQueueListener} annotated method. This allows for
 * consumers of this framework to define a specific {@link MessageBroker} implementation that they would like for annotated
 * method which is not provided by another annotation.
 */
@FunctionalInterface
public interface MessageBrokerFactory {
    /**
     * Construct a {@link MessageBroker} that will use the provided {@link MessageRetriever} and {@link MessageProcessor} built
     * from their corresponding factories.
     *
     * @param messageRetriever the retriever that should be used in this broker
     * @param messageProcessor the processor that should be used in this broker
     * @return a broker that will wrap the method
     * @see MessageRetrieverFactory for how the provided {@link MessageRetriever} is supplied to this method
     * @see MessageProcessorFactory for how the provided {@link MessageProcessor} is supplied to this method
     */
    MessageBroker createMessageBroker(MessageRetriever messageRetriever, MessageProcessor messageProcessor);
}
