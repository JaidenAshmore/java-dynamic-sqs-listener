package com.jashmore.sqs.argument;

import com.google.common.collect.ImmutableSet;

import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver;
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.argument.visibility.VisibilityExtenderArgumentResolver;
import lombok.experimental.Delegate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.Set;

/**
 * Default implementation of the {@link ArgumentResolverService} that applies all of the {@link ArgumentResolver}s that have been implemented as
 * part of this core package.
 *
 * <p>If you desire more/different resolves use the {@link DelegatingArgumentResolverService} to consume these.
 */
public class DefaultArgumentResolverService implements ArgumentResolverService {
    @Delegate
    private final DelegatingArgumentResolverService delegatingArgumentResolverService;

    public DefaultArgumentResolverService(final PayloadMapper payloadMapper,
                                          final SqsAsyncClient sqsAsyncClient) {
        final Set<ArgumentResolver> argumentResolvers = ImmutableSet.of(
                new PayloadArgumentResolver(payloadMapper),
                new MessageIdArgumentResolver(),
                new VisibilityExtenderArgumentResolver(sqsAsyncClient)
        );
        this.delegatingArgumentResolverService = new DelegatingArgumentResolverService(argumentResolvers);
    }
}
