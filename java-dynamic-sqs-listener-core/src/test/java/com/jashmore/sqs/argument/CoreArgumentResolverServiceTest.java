package com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.messageid.MessageId;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.util.Map;

public class CoreArgumentResolverServiceTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private ObjectMapper objectMapper = new ObjectMapper();

    private PayloadMapper payloadMapper = new JacksonPayloadMapper(objectMapper);

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private CoreArgumentResolverService service;

    @Before
    public void setUp() {
        service = new CoreArgumentResolverService(payloadMapper, sqsAsyncClient);
    }

    @Test
    public void shouldBeAbleToResolveMethodArgumentsWithDefaultResolvers() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class);
        final Map<String, String> payload = ImmutableMap.of("key", "value");
        final Message message = Message.builder()
                .body(objectMapper.writeValueAsString(payload))
                .messageId("messageId")
                .build();
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl("queueUrl").build();
        final MethodParameter mapFirstParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();
        final MethodParameter stringSecondParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[1])
                .parameterIndex(1)
                .build();

        // act
        final Object payloadArgument = service.resolveArgument(queueProperties, mapFirstParameter, message);
        final Object messageIdArgument = service.resolveArgument(queueProperties, stringSecondParameter, message);

        // assert
        assertThat(payloadArgument).isEqualTo(payload);
        assertThat(messageIdArgument).isEqualTo("messageId");
    }

    @SuppressWarnings( {"unused", "WeakerAccess"})
    public void method(@Payload final Map<String, String> payload, @MessageId final String messageId) {

    }
}
