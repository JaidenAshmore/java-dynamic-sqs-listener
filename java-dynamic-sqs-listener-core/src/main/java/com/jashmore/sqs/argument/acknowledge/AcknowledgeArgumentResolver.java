package com.jashmore.sqs.argument.acknowledge;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Parameter;

/**
 * Resolves message consumers with a parameter of type {@link Acknowledge} to an implementation that can be used
 * for the consumer to determine when the message is successfully completed and should be deleted from the queue.
 */
@AllArgsConstructor
public class AcknowledgeArgumentResolver implements ArgumentResolver {
    private final SqsAsyncClient sqsAsyncClient;

    @Override
    public boolean canResolveParameter(final Parameter parameter) {
        return parameter.getType() == Acknowledge.class;
    }

    @Override
    public Object resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final Parameter parameter,
                                              final Message message) throws ArgumentResolutionException {
        return new DefaultAcknowledge(sqsAsyncClient, queueProperties, message);
    }
}
