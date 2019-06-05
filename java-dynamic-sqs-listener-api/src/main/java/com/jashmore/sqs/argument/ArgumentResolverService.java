package com.jashmore.sqs.argument;

import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.processor.argument.Acknowledge;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Service used calculate argument values for particular parameters of a message consumer's method from a message.
 *
 * <p>For example, it may want to extract the message id of the message to be placed into a certain method parameter and this
 * service has the responsibility of doing this.
 *
 * <p>To increase testability and reduce complexity the resolution can be delegate to an individual {@link ArgumentResolver} to
 * handle one type of argument resolution. Therefore, each {@link ArgumentResolver} only needs to handle one type of argument
 * resolution greatly reducing complexity.
 *
 * <p>As there could be multiple messages all being processed at once, and therefore resolving many arguments concurrently,
 * the implementations of this class must be thread safe.
 */
@ThreadSafe
public interface ArgumentResolverService {
    /**
     * Determine the {@link ArgumentResolver} that should be used for processing an argument of the method.
     *
     * <p>Note that this does not need to be able to resolve the {@link Acknowledge} argument as that is provided by the {@link MessageProcessor}.
     *
     * @param methodParameter details about the method parameter
     * @return the resolver that should be used to resolve this parameter
     * @throws UnsupportedArgumentResolutionException if there is no available {@link ArgumentResolver}
     */
    ArgumentResolver<?> getArgumentResolver(MethodParameter methodParameter) throws UnsupportedArgumentResolutionException;
}
