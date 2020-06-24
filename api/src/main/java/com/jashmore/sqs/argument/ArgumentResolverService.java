package com.jashmore.sqs.argument;

import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.argument.VisibilityExtender;

/**
 * Service used to determine the {@link ArgumentResolver} that can be applied to a parameter of a method when it is executed during processing of
 * a message.
 *
 * <p>As there could be multiple messages all being processed at once, and therefore resolving many arguments concurrently,
 * the implementations of this class must be thread safe.
 */
@ThreadSafe
public interface ArgumentResolverService {
    /**
     * Determine the {@link ArgumentResolver} that should be used for processing an argument of the method.
     *
     * <p>Note that this does not resolve the {@link Acknowledge} or {@link VisibilityExtender} argument as that is provided by the {@link MessageProcessor}.
     *
     * @param methodParameter details about the method parameter
     * @return the resolver that should be used to resolve this parameter
     * @throws UnsupportedArgumentResolutionException if there is no available {@link ArgumentResolver}
     */
    ArgumentResolver<?> getArgumentResolver(MethodParameter methodParameter) throws UnsupportedArgumentResolutionException;
}
