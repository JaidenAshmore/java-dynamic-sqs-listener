package com.jashmore.sqs.argument.messageid;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.util.annotation.AnnotationUtils;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Parameter;

/**
 * Argument resolver for parameters annotated with the {@link MessageId} annotation.
 */
public class MessageIdArgumentResolver implements ArgumentResolver<String> {
    @Override
    public boolean canResolveParameter(final MethodParameter methodParameter) {
        return methodParameter.getParameter().getType().isAssignableFrom(String.class)
                && AnnotationUtils.findParameterAnnotation(methodParameter, MessageId.class).isPresent();
    }

    @Override
    public String resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final MethodParameter methodParameter,
                                              final Message message) throws ArgumentResolutionException {
        return message.messageId();
    }
}
