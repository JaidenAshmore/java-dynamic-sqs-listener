package com.jashmore.sqs.micronaut.container;

import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.util.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Abstract {@link MessageListenerContainerFactory} that will use annotations to determine whether the method can be wrapped or
 * not.
 *
 * @param <T> the type of annotation that the method should have for it to be wrapped
 */
public abstract class AbstractAnnotationMessageListenerContainerFactory<T extends Annotation> implements MessageListenerContainerFactory {

    @Override
    public boolean canHandleMethod(final Method method) {
        return AnnotationUtils.findMethodAnnotation(method, getAnnotationClass()).isPresent();
    }

    @Override
    public MessageListenerContainer buildContainer(final Object bean, final Method method) {
        final T annotation = AnnotationUtils
            .findMethodAnnotation(method, getAnnotationClass())
            .orElseThrow(() ->
                new RuntimeException("Trying to wrap method that does not contain annotation: @" + getAnnotationClass().getSimpleName())
            );

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
