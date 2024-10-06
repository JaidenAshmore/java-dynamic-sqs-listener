package com.jashmore.sqs.container;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Wrapper used to analyse methods in the application and determine if the method should be included in the messaging framework by wrapping that method in a
 * queue listener so that each message received will run the method.
 */
public interface MessageListenerContainerFactory {
    /**
     * Builds a {@link MessageListenerContainer} that will wrap the method and all messages for a queue will execute this method.
     *
     * @param bean   the specific bean for this method
     * @param method the method of the bean that will be run for each message
     * @return the container that will wrap this method if it can
     * @throws MessageListenerContainerInitialisationException if there was a known error building this container
     */
    Optional<MessageListenerContainer> buildContainer(Object bean, Method method) throws MessageListenerContainerInitialisationException;
}
