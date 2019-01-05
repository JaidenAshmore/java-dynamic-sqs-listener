package com.jashmore.sqs.spring;

import com.jashmore.sqs.spring.container.MessageListenerContainer;

import java.lang.reflect.Method;

/**
 * Wrapper used to analyse methods in the application and determine if the method should be included in the messaging framework by wrapping that method in a
 * queue listener so that each message received will run the method.
 */
public interface QueueWrapper {
    /**
     * Determine whether the method can be used to handle listening to queues.
     *
     * <p>This could be checking that the method is in a certain format or has an annotation indicating that it can be wrapped.
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
