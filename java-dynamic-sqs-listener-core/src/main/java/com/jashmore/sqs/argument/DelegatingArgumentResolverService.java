package com.jashmore.sqs.argument;

import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import lombok.AllArgsConstructor;

import java.lang.reflect.Parameter;
import java.util.Set;

/**
 * Delegates the resolving of an argument in the method using a list of {@link ArgumentResolver}s.
 *
 * <p>Note that a {@link Set} is used for the {@link ArgumentResolver}s because the ordering of them should not impact the result of the
 * resolution of the argument.
 */
@AllArgsConstructor
public class DelegatingArgumentResolverService implements ArgumentResolverService {
    private final Set<ArgumentResolver> argumentResolvers;

    @Override
    public Object resolveArgument(final QueueProperties queueProperties, final Parameter parameter, final Message message) {
        for (final ArgumentResolver resolver : argumentResolvers) {
            if (!resolver.canResolveParameter(parameter)) {
                continue;
            }

            try {
                return resolver.resolveArgumentForParameter(queueProperties, parameter, message);
            } catch (final Throwable throwable) {
                if (ArgumentResolutionException.class.isAssignableFrom(throwable.getClass())) {
                    throw throwable;
                }

                throw new ArgumentResolutionException("Error obtaining an argument value for parameter", throwable);
            }
        }

        throw new ArgumentResolutionException("No ArgumentResolver found that can process this parameter");
    }
}
