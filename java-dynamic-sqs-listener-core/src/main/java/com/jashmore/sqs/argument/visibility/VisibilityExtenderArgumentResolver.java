package com.jashmore.sqs.argument.visibility;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.argument.payload.Payload;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Resolves message consumer's with a parameter of type {@link VisibilityExtender} to an implementation that can be used
 * for the consumer to request the increase of visibility of the message.
 *
 * <p>Message consumer parameters with the {@link VisibilityExtender} type should not have any annotations that would result in another
 * {@link ArgumentResolver} from attempting to resolve the parameter. For example you should not have a method of type {@link VisibilityExtender} and also
 * with the {@link Payload} annotation.
 */
public class VisibilityExtenderArgumentResolver implements ArgumentResolver<VisibilityExtender> {
    private final SqsAsyncClient sqsAsyncClient;

    public VisibilityExtenderArgumentResolver(final SqsAsyncClient sqsAsyncClient) {
        this.sqsAsyncClient = sqsAsyncClient;
    }

    @Override
    public boolean canResolveParameter(final MethodParameter methodParameter) {
        return methodParameter.getParameter().getType() == VisibilityExtender.class;
    }

    @Override
    public VisibilityExtender resolveArgumentForParameter(final QueueProperties queueProperties,
                                                          final MethodParameter methodParameter,
                                                          final Message message) throws ArgumentResolutionException {
        return new DefaultVisibilityExtender(sqsAsyncClient, queueProperties, message);
    }
}
