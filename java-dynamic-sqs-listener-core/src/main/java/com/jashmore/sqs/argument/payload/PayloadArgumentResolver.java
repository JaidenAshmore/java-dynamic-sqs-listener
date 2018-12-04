package com.jashmore.sqs.argument.payload;

import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMappingException;
import lombok.AllArgsConstructor;

import java.lang.reflect.Parameter;

/**
 * Argument resolver that is able to create an argument from the body of a message.
 *
 * <p>This should allow for the serialisation of the body to a specific Java Bean via an implementation of the {@link PayloadMapper}.
 *
 * @see Message#body for the payload that will be consumed
 */
@AllArgsConstructor
public class PayloadArgumentResolver implements ArgumentResolver {
    private final PayloadMapper payloadMapper;

    @Override
    public boolean canResolveParameter(final Parameter parameter) {
        return parameter.getAnnotation(Payload.class) != null;
    }

    @Override
    public Object resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final Parameter parameter,
                                              final Message message) throws ArgumentResolutionException {
        try {
            return payloadMapper.map(message, parameter.getType());
        } catch (final PayloadMappingException payloadMappingException) {
            throw new ArgumentResolutionException(payloadMappingException);
        }
    }
}
