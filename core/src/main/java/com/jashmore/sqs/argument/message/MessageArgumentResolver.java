package com.jashmore.sqs.argument.message;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.MethodParameter;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * {@link ArgumentResolver} that can be used to get the entire message that is being processed.
 *
 * <p>This can be useful if the current {@link ArgumentResolver}s do not provide the functionality required and a custom one does not want to be built. Another
 * use case could be when the entire message is needed to forward to another queue.
 */
public class MessageArgumentResolver implements ArgumentResolver<Message> {

    @Override
    public boolean canResolveParameter(final MethodParameter methodParameter) {
        return methodParameter.getParameter().getType() == Message.class;
    }

    @Override
    public Message resolveArgumentForParameter(
        final QueueProperties queueProperties,
        final MethodParameter methodParameter,
        final Message message
    ) throws ArgumentResolutionException {
        return message;
    }
}
