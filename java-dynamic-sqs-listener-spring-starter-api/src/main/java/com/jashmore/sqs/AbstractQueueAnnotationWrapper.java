package com.jashmore.sqs;

import com.jashmore.sqs.container.MessageListenerContainer;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Abstract {@link QueueWrapper} that will use annotations to determine whether the method can be wrapped or
 * not.
 *
 * @param <T> the type of annotation that the method should have for it to be wrapped
 */
public abstract class AbstractQueueAnnotationWrapper<T extends Annotation> implements QueueWrapper {
    @Override
    public boolean canWrapMethod(final Method method) {
        return method.isAnnotationPresent(getAnnotationClass());
    }

    @Override
    public MessageListenerContainer wrapMethod(final Object bean, final Method method) {
        final T annotation = method.getAnnotation(getAnnotationClass());
        if (annotation == null) {
            throw new RuntimeException("Wrapping method with annotation but none exist");
        }
        return wrapMethodContainingAnnotation(bean, method, annotation);
    }

    /**
     * The class of the annotation that should be checked against.
     *
     * @return the class for the annotation for this queue wrapper
     */
    protected abstract Class<T> getAnnotationClass();

    /**
     * Helper method that provides the annotation found on the method with the method and bean to be wrapped.
     *
     * @param bean       the bean instance for the corresponding method
     * @param method     the method that should be wrapped
     * @param annotation the annotation found on the method containing details about this listener
     * @return the container that wraps the method for usage by the queue listeners
     */
    protected abstract MessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method, T annotation);
}
