package com.jashmore.sqs.argument;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.acknowledge.AcknowledgeArgumentResolver;
import com.jashmore.sqs.argument.heartbeat.HeartbeatArgumentResolver;
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver;
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.util.Immutables;

import java.lang.reflect.Parameter;
import java.util.Set;

/**
 * Default implementation of the {@link ArgumentResolverService} that applies all of the {@link ArgumentResolver}s that have been implemented as
 * part of this core package.
 *
 * <p>If you desire more/different resolves use the {@link DelegatingArgumentResolverService} to consume these.
 */
public class DefaultArgumentResolverService implements ArgumentResolverService {
    private final DelegatingArgumentResolverService delegatingArgumentResolverService;

    public DefaultArgumentResolverService(final PayloadMapper payloadMapper,
                                          final AmazonSQSAsync amazonSqsAsync) {
        final Set<ArgumentResolver> argumentResolvers = Immutables.immutableSet(
                new PayloadArgumentResolver(payloadMapper),
                new MessageIdArgumentResolver(),
                new AcknowledgeArgumentResolver(amazonSqsAsync),
                new HeartbeatArgumentResolver(amazonSqsAsync)
        );
        this.delegatingArgumentResolverService = new DelegatingArgumentResolverService(argumentResolvers);
    }

    @Override
    public Object resolveArgument(final QueueProperties queueProperties,
                                  final Parameter parameter,
                                  final Message message) throws ArgumentResolutionException {
        return delegatingArgumentResolverService.resolveArgument(queueProperties, parameter, message);
    }
}
