package com.jashmore.sqs.argument;

import static com.jashmore.sqs.util.collections.CollectionUtils.immutableListOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.argument.attribute.MessageAttributeArgumentResolver;
import com.jashmore.sqs.argument.attribute.MessageSystemAttributeArgumentResolver;
import com.jashmore.sqs.argument.message.MessageArgumentResolver;
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver;
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import java.util.List;
import lombok.experimental.Delegate;

/**
 * Default implementation of the {@link ArgumentResolverService} that applies all of the {@link ArgumentResolver}s that have been implemented as
 * part of this core package.
 *
 * <p>If you desire more/different resolves use the {@link DelegatingArgumentResolverService} to consume these.
 */
public class CoreArgumentResolverService implements ArgumentResolverService {
    @Delegate
    private final DelegatingArgumentResolverService delegatingArgumentResolverService;

    public CoreArgumentResolverService(final PayloadMapper payloadMapper, final ObjectMapper objectMapper) {
        final List<ArgumentResolver<?>> argumentResolvers = immutableListOf(
            new PayloadArgumentResolver(payloadMapper),
            new MessageIdArgumentResolver(),
            new MessageAttributeArgumentResolver(objectMapper),
            new MessageSystemAttributeArgumentResolver(),
            new MessageArgumentResolver()
        );
        this.delegatingArgumentResolverService = new DelegatingArgumentResolverService(argumentResolvers);
    }
}
