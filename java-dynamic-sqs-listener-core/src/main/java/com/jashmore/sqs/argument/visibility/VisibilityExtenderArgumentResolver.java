package com.jashmore.sqs.argument.visibility;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.payload.Payload;

import java.lang.reflect.Parameter;

/**
 * Resolves message consumer's with a parameter of type {@link VisibilityExtender} to an implementation that can be used
 * for the consumer to request the increase of visibility of the message.
 *
 * <p>Message consumer parameters with the {@link VisibilityExtender} type should not have any annotations that would result in another
 * {@link ArgumentResolver} from attempting to resolve the parameter. For example you should not have a method of type {@link VisibilityExtender} and also
 * with the {@link Payload} annotation.
 */
public class VisibilityExtenderArgumentResolver implements ArgumentResolver {
    private final AmazonSQSAsync amazonSqsAsync;

    public VisibilityExtenderArgumentResolver(final AmazonSQSAsync amazonSqsAsync) {
        this.amazonSqsAsync = amazonSqsAsync;
    }

    @Override
    public boolean canResolveParameter(final Parameter parameter) {
        return parameter.getType() == VisibilityExtender.class;
    }

    @Override
    public Object resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final Parameter parameter,
                                              final Message message) throws ArgumentResolutionException {
        return new DefaultVisibilityExtender(amazonSqsAsync, queueProperties, message);
    }
}
