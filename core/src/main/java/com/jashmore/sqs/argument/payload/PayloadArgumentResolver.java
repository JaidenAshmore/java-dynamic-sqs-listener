package com.jashmore.sqs.argument.payload;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMappingException;
import com.jashmore.sqs.util.annotation.AnnotationUtils;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Argument resolver that is able to create an argument from the body of a message.
 *
 * <p>This should allow for the serialisation of the body to a specific Java Bean via an implementation of the {@link PayloadMapper}.
 *
 * @see Message#body() for the payload that will be consumed
 */
@AllArgsConstructor
public class PayloadArgumentResolver implements ArgumentResolver<Object> {

    private final PayloadMapper payloadMapper;

    @Override
    public boolean canResolveParameter(final MethodParameter methodParameter) {
        return AnnotationUtils.findParameterAnnotation(methodParameter, Payload.class).isPresent();
    }

    @Override
    public Object resolveArgumentForParameter(
        final QueueProperties queueProperties,
        final MethodParameter methodParameter,
        final Message message
    )
        throws ArgumentResolutionException {
        try {
            return payloadMapper.map(message, methodParameter.getParameter().getType());
        } catch (final PayloadMappingException payloadMappingException) {
            throw new ArgumentResolutionException(payloadMappingException);
        }
    }
}
