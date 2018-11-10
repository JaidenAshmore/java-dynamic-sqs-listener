package com.jashmore.sqs.argument.heartbeat;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.payload.Payload;

import java.lang.reflect.Parameter;

/**
 * Resolves message consumer's with a parameter of type {@link Heartbeat} to an implementation that can be used
 * for the consumer to request the increase of visibility of the message.
 *
 * <p>Message consumer parameters with the {@link Heartbeat} type should not have any annotations that would result in another {@link ArgumentResolver} from
 * attempting to resolve the parameter. For example you should not have a method of type {@link Heartbeat} and also with the {@link Payload} annotation.
 */
public class HeartbeatArgumentResolver implements ArgumentResolver {
    private final AmazonSQSAsync amazonSqsAsync;

    public HeartbeatArgumentResolver(AmazonSQSAsync amazonSqsAsync) {
        this.amazonSqsAsync = amazonSqsAsync;
    }

    @Override
    public boolean canResolveParameter(final Parameter parameter) {
        return parameter.getType() == Heartbeat.class;
    }

    @Override
    public Object resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final Parameter parameter,
                                              final Message message) throws ArgumentResolutionException {
        return new DefaultHeartbeat(amazonSqsAsync, queueProperties, message);
    }
}
