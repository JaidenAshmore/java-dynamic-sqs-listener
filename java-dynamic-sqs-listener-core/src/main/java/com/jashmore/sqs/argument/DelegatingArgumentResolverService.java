package com.jashmore.sqs.argument;

import com.jashmore.sqs.QueueProperties;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Set;

/**
 * Delegates the resolving of an argument in the method using a list of {@link ArgumentResolver}s.
 *
 * <p>Note that a {@link Set} is used for the {@link ArgumentResolver}s because the ordering of them should not impact the result of the
 * resolution of the argument.
 */
@AllArgsConstructor
public class DelegatingArgumentResolverService implements ArgumentResolverService {
    private final Set<ArgumentResolver<?>> argumentResolvers;

    @Override
    public Object resolveArgument(final QueueProperties queueProperties, final MethodParameter methodParameter, final Message message) {
        return argumentResolvers.stream()
                .filter(resolver -> resolver.canResolveParameter(methodParameter))
                .map(resolver -> {
                    try {
                        return resolver.resolveArgumentForParameter(queueProperties, methodParameter, message);
                    } catch (final RuntimeException runtimeException) {
                        // Make sure to wrap any unintended exceptions with the expected exception for errors
                        if (!ArgumentResolutionException.class.isAssignableFrom(runtimeException.getClass())) {
                            throw new ArgumentResolutionException("Error obtaining an argument value for parameter", runtimeException);
                        }

                        throw runtimeException;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new ArgumentResolutionException("No ArgumentResolver found that can process this parameter"));
    }
}
