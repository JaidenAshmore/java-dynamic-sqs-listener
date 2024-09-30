package com.jashmore.sqs.micronaut.decorator;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Factory used to build a {@link MessageProcessingDecorator} that will be attached to a specific message listener method.
 *
 * <p>This is separate to any global {@link MessageProcessingDecorator}s, which can be defined as beans in the Spring Application and attached to all the
 * message listeners.
 */
public interface MessageProcessingDecoratorFactory<T extends MessageProcessingDecorator> {
    /**
     * Build the optional decorator for the provided method.
     *
     * @param sqsAsyncClient  the client that is used for this message listener
     * @param queueProperties details about the queue
     * @param bean the bean object that the message listener is running on
     * @param method the message listener method
     * @return an optional decorator to attach to the message listener
     * @throws MessageProcessingDecoratorFactoryException if the decorator cannot be built due to the method being in the incorrect format
     *     or if an error occurred building the decorator
     */
    Optional<T> buildDecorator(
        SqsAsyncClient sqsAsyncClient,
        QueueProperties queueProperties,
        String identifier,
        Object bean,
        Method method
    );
}
