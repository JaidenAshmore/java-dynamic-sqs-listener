package com.jashmore.sqs.container.custom;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.processor.MessageProcessor;

import java.lang.reflect.Method;

/**
 * Factory used to build a {@link MessageProcessor} for a {@link CustomQueueListener @ConfigurableQueueListener} annotated method. This allows for
 * consumers of this framework to define a specific {@link MessageProcessor} implementation that they would like for annotated
 * method which is not provided by another annotation.
 */
@FunctionalInterface
public interface MessageProcessorFactory {
    /**
     * Construct a {@link MessageProcessor} that will be used for processing the messages of the queue via the given bean and method.
     *
     * @param queueProperties details about the queue that the message is being processed for
     * @param bean            the bean for the method that will be executed for a message
     * @param method          the message of the bean that will be executed for this message
     * @return a {@link MessageProcessor} that will process messages by executing this bean method
     */
    MessageProcessor createMessageProcessor(QueueProperties queueProperties,
                                            Object bean,
                                            Method method);
}
