package com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.argument.attribute.MessageAttribute;
import com.jashmore.sqs.argument.attribute.MessageAttributeArgumentResolver;
import com.jashmore.sqs.argument.attribute.MessageSystemAttribute;
import com.jashmore.sqs.argument.attribute.MessageSystemAttributeArgumentResolver;
import com.jashmore.sqs.argument.message.MessageArgumentResolver;
import com.jashmore.sqs.argument.messageid.MessageId;
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.lang.reflect.Method;
import java.util.Map;

public class CoreArgumentResolverServiceTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private ObjectMapper objectMapper = new ObjectMapper();

    private PayloadMapper payloadMapper = new JacksonPayloadMapper(objectMapper);

    private CoreArgumentResolverService service;

    @Before
    public void setUp() {
        service = new CoreArgumentResolverService(payloadMapper, objectMapper);
    }

    @Test
    public void shouldReturnPayloadArgumentResolverForPayloadMethodArguments() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class, String.class, String.class, Message.class);
        final MethodParameter messagePayloadParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolver<?> payloadArgument = service.getArgumentResolver(messagePayloadParameter);

        // assert
        assertThat(payloadArgument).isInstanceOf(PayloadArgumentResolver.class);
    }


    @Test
    public void shouldReturnMessageIdArgumentResolverForMessageIdMethodArguments() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class, String.class, String.class, Message.class);
        final MethodParameter messageIdParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[1])
                .parameterIndex(1)
                .build();

        // act
        final ArgumentResolver<?> messageIdArgument = service.getArgumentResolver(messageIdParameter);

        // assert
        assertThat(messageIdArgument).isInstanceOf(MessageIdArgumentResolver.class);
    }

    @Test
    public void shouldReturnMessageAttributeArgumentResolverForMessageAttributeMethodArguments() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class, String.class, String.class, Message.class);
        final MethodParameter messagePayloadParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[2])
                .parameterIndex(2)
                .build();

        // act
        final ArgumentResolver<?> payloadArgument = service.getArgumentResolver(messagePayloadParameter);

        // assert
        assertThat(payloadArgument).isInstanceOf(MessageAttributeArgumentResolver.class);
    }

    @Test
    public void shouldReturnSystemMessageAttributeArgumentResolverForMessageSystemAttributeMethodArguments() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class, String.class, String.class, Message.class);
        final MethodParameter messagePayloadParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[3])
                .parameterIndex(3)
                .build();

        // act
        final ArgumentResolver<?> payloadArgument = service.getArgumentResolver(messagePayloadParameter);

        // assert
        assertThat(payloadArgument).isInstanceOf(MessageSystemAttributeArgumentResolver.class);
    }

    @Test
    public void shouldReturnMessageArgumentResolverForMessageMethodArguments() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class, String.class, String.class, Message.class);
        final MethodParameter messagePayloadParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[4])
                .parameterIndex(4)
                .build();

        // act
        final ArgumentResolver<?> payloadArgument = service.getArgumentResolver(messagePayloadParameter);

        // assert
        assertThat(payloadArgument).isInstanceOf(MessageArgumentResolver.class);
    }

    @SuppressWarnings( {"unused"})
    public void method(@Payload final Map<String, String> payload,
                       @MessageId final String messageId,
                       @MessageAttribute("key") final String attribute,
                       @MessageSystemAttribute(MessageSystemAttributeName.SEQUENCE_NUMBER) final String sequenceNumber,
                       final Message message) {

    }
}
