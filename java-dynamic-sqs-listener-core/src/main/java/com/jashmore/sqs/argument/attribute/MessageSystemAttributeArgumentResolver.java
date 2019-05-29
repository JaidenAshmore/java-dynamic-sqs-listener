package com.jashmore.sqs.argument.attribute;

import static java.time.ZoneOffset.UTC;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.APPROXIMATE_FIRST_RECEIVE_TIMESTAMP;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.SENT_TIMESTAMP;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.util.annotation.AnnotationUtils;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

public class MessageSystemAttributeArgumentResolver implements ArgumentResolver<Object> {
    @Override
    public boolean canResolveParameter(final MethodParameter methodParameter) {
        return AnnotationUtils.findParameterAnnotation(methodParameter, MessageSystemAttribute.class).isPresent();
    }

    @Override
    public Object resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final MethodParameter methodParameter,
                                              final Message message) throws ArgumentResolutionException {
        final MessageSystemAttribute annotation = AnnotationUtils.findParameterAnnotation(methodParameter, MessageSystemAttribute.class)
                .orElseThrow(() -> new ArgumentResolutionException(
                        "Parameter passed in does not contain the MessageSystemAttribute annotation when it should"
                ));

        final MessageSystemAttributeName messageSystemAttributeName = annotation.value();
        final Optional<String> optionalAttributeValue = Optional.ofNullable(message.attributes().get(messageSystemAttributeName));

        if (!optionalAttributeValue.isPresent() ) {
            if (annotation.required()) {
                throw new ArgumentResolutionException("Missing system attribute with name: " + messageSystemAttributeName.toString());
            }

            return null;
        }
        final String attributeValue = optionalAttributeValue.get();

        final Class<?> parameterType = methodParameter.getParameter().getType();
        try {
            if (parameterType == String.class) {
                return attributeValue;
            }

            if (parameterType == Integer.class || parameterType == int.class) {
                return Integer.parseInt(attributeValue);
            }

            if (parameterType == Long.class || parameterType == long.class) {
                return Long.parseLong(attributeValue);
            }
        } catch (final RuntimeException exception) {
            throw new ArgumentResolutionException("Error parsing message attribute: " + messageSystemAttributeName.toString(), exception);
        }

        if (messageSystemAttributeName == SENT_TIMESTAMP || messageSystemAttributeName == APPROXIMATE_FIRST_RECEIVE_TIMESTAMP) {
            return handleTimeStampAttributes(methodParameter.getParameter().getType(), messageSystemAttributeName, attributeValue);
        }

        throw new ArgumentResolutionException("Unsupported parameter type " + parameterType.getName()
                + " for system attribute " + messageSystemAttributeName.toString());
    }

    private Object handleTimeStampAttributes(final Class<?> parameterType,
                                             final MessageSystemAttributeName messageSystemAttributeName,
                                             final String attributeValue) {
        if (parameterType == Instant.class) {
            return Instant.ofEpochMilli(Long.parseLong(attributeValue));
        }

        if (parameterType == OffsetDateTime.class) {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(attributeValue)), UTC);
        }

        throw new ArgumentResolutionException("Unsupported parameter type " + parameterType.getName()
                + " for system attribute " + messageSystemAttributeName.toString());
    }
}
