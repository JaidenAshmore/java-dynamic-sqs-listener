package com.jashmore.sqs.argument;

import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.QueueProperties;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Used to resolve an argument in the method processing a messages if it can resolve it.
 *
 * <p>As there could be multiple messages all being processed at once, and therefore resolving many arguments concurrently,
 * the implementations of this class must be thread safe.
 *
 * @param <T> the type of object that is returned when an argument is resolved
 */
@ThreadSafe
public interface ArgumentResolver<T> {
    /**
     * Determine whether the provided {@link MethodParameter} is able to be resolved by this resolver.
     *
     * @param methodParameter details about a parameter for the method
     * @return whether this parameter value can be obtained from this resolver
     */
    boolean canResolveParameter(MethodParameter methodParameter);

    /**
     * Resolve the argument for the given {@link MethodParameter}.
     *
     * @param queueProperties details about the queue that the arguments will be resolved for
     * @param methodParameter details about a parameter for the method
     * @param message         the message being processed by this queue
     * @return the value of the argument
     * @throws ArgumentResolutionException when there was an error determine the parameter argument value
     */
    T resolveArgumentForParameter(QueueProperties queueProperties, MethodParameter methodParameter, Message message) throws ArgumentResolutionException;
}
