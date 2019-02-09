package com.jashmore.sqs.argument.messageid;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Parameter;

/**
 * Argument resolver for parameters annotated with the {@link MessageId} annotation.
 */
public class MessageIdArgumentResolver implements ArgumentResolver<String> {
    @Override
    public boolean canResolveParameter(final Parameter parameter) {
        return parameter.getAnnotation(MessageId.class) != null && parameter.getType().isAssignableFrom(String.class);
    }

    @Override
    public String resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final Parameter parameter,
                                              final Message message) throws ArgumentResolutionException {
        return message.messageId();
    }
}
