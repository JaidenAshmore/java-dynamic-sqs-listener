package com.jashmore.sqs.util.annotation;

import com.jashmore.documentation.annotations.Nonnull;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.util.Preconditions;
import lombok.experimental.UtilityClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Function;

/**
 * These annotation utility method are written to get past the problem of CGLib and other proxying libraries that extend the base classes to add extra
 * functionality like Aspect Orientated Programming (AOP) point cuts, think wrapping methods in logs or metrics. It does this by extending the base class
 * and apply the logic in the proxied class. As these proxied classes do not copy the base class method annotations doing a simple
 * {@link Method#getAnnotation(Class)} will return null even though the base class originally had that annotation. Therefore, this provides helper methods
 * to traverse through the list of superclasses until it finds the method with the annotation or it gets to the {@link Object} class.
 *
 * <p>This does not support annotations on interfaces as for the use case of SQS Listeners I do not think it makes sense to add the annotation to an
 * interface and to have many classes implement this interface expecting that they would all match the annotation. Therefore, for simplicity in that
 * sense this feature is not supported.
 *
 * <p>This also does not support bridge methods as this overly complicates the logic and I also don't see the need for applying generics and extending
 * these SQS Listener methods with generics parameters.  For example, I don't see why we would ever have a class like the following.
 *
 * <pre class="code">
 * public abstract class SqsListener&lt;T&gt; {
 *     &#064;QueueListener("myQueue")
 *     public abstract void method(&#064;Payload T payload);
 * }
 *
 * public class StringSqsListener&lt;String&gt; {
 *     public void method(String payload) {
 *
 *     }
 * }
 * </pre>
 *
 * <p>Or another scenario where the logic is encapsulated in the abstract class like the following.
 *
 * <pre class="code">
 * public abstract class SqsListener&lt;T&gt; {
 *     public void method(&#064;Payload T payload) {
 *         log.info("Message: {}", payload);
 *     }
 * }
 *
 * public class StringSqsListener&lt;String&gt; {
 *     &#064;QueueListener("myQueue")
 *     public void method(&#064;Payload String payload) {
 *         super.method(payload);
 *     }
 * }
 * </pre>
 *
 * <p>For both of these examples if the goal is to reduce goal duplication, prefer composition over inheritance. E.g. create a service that is generic
 * but the listener class are still solid classes without applying generics. For more information about Java bridge methods take a look at
 * <a href="https://docs.oracle.com/javase/tutorial/java/generics/bridgeMethods.html">Java Bridge Methods</a>.
 */
@UtilityClass
public class AnnotationUtils {
    /**
     * Get the annotation on a method by looking on the method of this class as well as traversing the methods for all superclasses of the method, returning
     * the first annotation that it finds otherwise returning an {@link Optional#empty()} if none of the class methods contain the annotation.
     *
     * @param methodToProcess method to find annotations for
     * @param annotationClass the annotation to find for the method
     * @param <A>             the type of the annotation
     * @return the found annotation in an {@link Optional} otherwise an {@link Optional#empty()} is returned
     */
    @Nonnull
    public <A extends Annotation> Optional<A> findMethodAnnotation(@Nonnull final Method methodToProcess, @Nonnull final Class<A> annotationClass) {
        Preconditions.checkNotNull(methodToProcess, "methodToProcess should not be null");
        Preconditions.checkNotNull(annotationClass, "annotationClass should not be null");

        return findForEachChainedMethodStack(methodToProcess, method -> Optional.ofNullable(method.getAnnotation(annotationClass)));
    }

    /**
     * Find the annotation for the parameter of a given method by traversing the chain of super classes until it eventually finds a parameter with the
     * annotation or there are no more classes to traverse.
     *
     * @param methodParameterToProcess the method parameter to find the annotation for
     * @param annotationClass          the class of the annotation to find
     * @param <A>                      the type of the annotation
     * @return the found annotation in an {@link Optional} otherwise an {@link Optional#empty()} is returned
     */
    @Nonnull
    public <A extends Annotation> Optional<A> findParameterAnnotation(@Nonnull final MethodParameter methodParameterToProcess,
                                                                      @Nonnull final Class<A> annotationClass) {
        return findForEachChainedMethodStack(methodParameterToProcess.getMethod(),
                method -> Optional.ofNullable(method.getParameters()[methodParameterToProcess.getParameterIndex()].getAnnotation(annotationClass)));
    }

    /**
     * Go through the chain of class hierarchy for the provided method and apply a function to the method to optional find some value.  If the visitor
     * applied to the function does not find it, it will continue up the chain of classes until it has found it or there are no more classes to traverse.
     *
     * @param method        the method to visit
     * @param methodVisitor the function that will try and find the value for the method
     * @param <T>           the type of value that is being searched for
     * @return the found value in an {@link Optional} otherwise an {@link Optional#empty()} is returned
     */
    private <T> Optional<T> findForEachChainedMethodStack(final Method method, final Function<Method, Optional<T>> methodVisitor) {
        final Optional<T> matchedValue = methodVisitor.apply(method);

        if (matchedValue.isPresent()) {
            return matchedValue;
        }

        final Class<?> methodClass = method.getDeclaringClass();

        final Class<?> superClass = methodClass.getSuperclass();
        if (superClass == Object.class || superClass == null) {
            return Optional.empty();
        }

        try {
            final Method superClassMethod = superClass.getMethod(method.getName(), method.getParameterTypes());

            return findForEachChainedMethodStack(superClassMethod, methodVisitor);
        } catch (final NoSuchMethodException noSuchMethodException) {
            return Optional.empty();
        }
    }
}
