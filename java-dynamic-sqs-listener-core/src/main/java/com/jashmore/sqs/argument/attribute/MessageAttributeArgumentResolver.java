package com.jashmore.sqs.argument.attribute;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.util.annotation.AnnotationUtils;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * An {@link ArgumentResolver} that is able to handle the extraction of information from the attributes of the SQS message.
 *
 * <p>This will attempt to do its best in casting the contents of the message attribute to the type of tha parameter. For example, if the contents of
 * the message attribute is binary (byte[]) and the parameter is a POJO, it will attempt to serialise using the {@link ObjectMapper#readValue(byte[], Class)}.
 *
 * <p>It is the responsibility of the consumer to make sure that the type of the parameter is correct in regards to the content of the message attribute. For
 * example, this resolver ignores all helper data types after the main, e.g. Number.float data types will have the float ignored.
 *
 * <p>This current implementation uses the Jackson {@link ObjectMapper} to perform all of the parsing and there is the potential for a future version of this
 * library to split this out so that Jackson isn't a required dependency.
 *
 * @see <a href="https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-attributes.html">SQS Message Attributes</a>
 * @see MessageAttributeValue
 */
public class MessageAttributeArgumentResolver implements ArgumentResolver<Object> {
    private final ObjectMapper objectMapper;

    public MessageAttributeArgumentResolver(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canResolveParameter(final MethodParameter methodParameter) {
        return AnnotationUtils.findParameterAnnotation(methodParameter, MessageAttribute.class).isPresent();
    }

    @Override
    public Object resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final MethodParameter methodParameter,
                                              final Message message) throws ArgumentResolutionException {
        final MessageAttribute annotation = AnnotationUtils.findParameterAnnotation(methodParameter, MessageAttribute.class)
                .orElseThrow(() -> new ArgumentResolutionException("Parameter passed in does not contain the MessageAttribute annotation when it should"));

        final String attributeName = annotation.value();

        final Optional<MessageAttributeValue> optionalMessageAttributeValue = Optional.ofNullable(message.messageAttributes().get(attributeName));

        if (!optionalMessageAttributeValue.isPresent()) {
            if (annotation.required()) {
                throw new ArgumentResolutionException("Required Message Attribute '" + attributeName + "' is missing from message");
            }

            return null;
        }

        final MessageAttributeValue messageAttributeValue = optionalMessageAttributeValue.get();

        if (messageAttributeValue.dataType().startsWith(MessageAttributeDataTypes.STRING.getValue())
                || messageAttributeValue.dataType().startsWith(MessageAttributeDataTypes.NUMBER.getValue())) {
            return handleStringParameterValue(methodParameter, messageAttributeValue, attributeName);
        } else if (messageAttributeValue.dataType().startsWith(MessageAttributeDataTypes.BINARY.getValue())) {
            return handleByteParameterValue(methodParameter, messageAttributeValue);
        }

        throw new ArgumentResolutionException("Cannot parse message attribute due to unknown data type '" + messageAttributeValue.dataType() + "'");
    }

    /**
     * Handle resolving the argument from the string contents of the attribute.
     *
     * @param methodParameter       the parameter of the method to resolve
     * @param messageAttributeValue the value of the message attribute
     * @param attributeName         the name of the attribute that is being consumed
     * @return the resolved argument from the attribute
     */
    private Object handleStringParameterValue(final MethodParameter methodParameter,
                                              final MessageAttributeValue messageAttributeValue,
                                              final String attributeName) {
        if (methodParameter.getParameter().getType().isAssignableFrom(String.class)) {
            return messageAttributeValue.stringValue();
        }

        try {
            return objectMapper.readValue(messageAttributeValue.stringValue(), methodParameter.getParameter().getType());
        } catch (final IOException ioException) {
            throw new ArgumentResolutionException("Error parsing Message Attribute '" + attributeName + "'", ioException);
        }
    }

    /**
     * Handle an attribute that contains the data as bytes.
     *
     * @param methodParameter       details about the parameter to parse the message attribute into
     * @param messageAttributeValue value of the message attribute
     * @return the argument resolved for this attribute
     */
    private Object handleByteParameterValue(final MethodParameter methodParameter,
                                            final MessageAttributeValue messageAttributeValue) {
        final byte[] byteArray = messageAttributeValue.binaryValue().asByteArray();
        final Class<?> parameterClass = methodParameter.getParameter().getType();
        if (parameterClass == byte[].class) {
            return byteArray;
        }

        if (parameterClass.isAssignableFrom(String.class)) {
            return new String(byteArray, Charset.forName("UTF-8"));
        }

        try {
            return objectMapper.readValue(byteArray, parameterClass);
        } catch (final IOException ioException) {
            throw new ArgumentResolutionException("Failure to parse binary bytes to '" + parameterClass.getName() + "'", ioException);
        }
    }
}
