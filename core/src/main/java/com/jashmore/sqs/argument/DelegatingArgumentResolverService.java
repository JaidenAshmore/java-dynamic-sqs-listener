package com.jashmore.sqs.argument;

import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;

/**
 * Delegates the resolving of an argument in the method using a list of {@link ArgumentResolver}s.
 *
 * <p>Note that a {@link Set} is used for the {@link ArgumentResolver}s because the ordering of them should not impact the result of the
 * resolution of the argument.
 */
@AllArgsConstructor
public class DelegatingArgumentResolverService implements ArgumentResolverService {

    private final List<ArgumentResolver<?>> argumentResolvers;

    @Override
    public ArgumentResolver<?> getArgumentResolver(final MethodParameter methodParameter) throws UnsupportedArgumentResolutionException {
        return argumentResolvers
            .stream()
            .filter(resolver -> resolver.canResolveParameter(methodParameter))
            .findFirst()
            .orElseThrow(() -> new UnsupportedArgumentResolutionException(methodParameter));
    }
}
