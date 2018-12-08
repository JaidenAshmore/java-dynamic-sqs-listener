package com.jashmore.sqs;

import com.jashmore.sqs.container.MessageListenerContainer;

import java.lang.reflect.Method;

/**
 * Processor used to analyse methods in the Spring Boot app and determine if the method should be included in the
 * messaging framework.
 */
public interface QueueWrapper {
    /**
     * Determine whether the method should be used to handle listening to queues.
     *
     * @param method the method to check against
     * @return whether this method should be wrapped in a queue listener
     */
    boolean canWrapMethod(Method method);

    /**
     * Wrap a method with a {@link MessageListenerContainer} that will handle the messages being processed.
     *
     * @param bean   the specific bean for this method
     * @param method the method of the bean that will be run for each message
     * @return the container that will wrap this method
     */
    MessageListenerContainer wrapMethod(Object bean, Method method);
}
