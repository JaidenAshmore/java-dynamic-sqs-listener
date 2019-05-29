package com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.attribute.MessageAttribute;
import com.jashmore.sqs.argument.attribute.MessageAttributeDataTypes;
import com.jashmore.sqs.argument.attribute.MessageSystemAttribute;
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
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

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
        service = new CoreArgumentResolverService(payloadMapper, sqsAsyncClient, objectMapper);
    }

    @Test
    public void shouldBeAbleToResolvePayloadParameters() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class, String.class, String.class);
        final Map<String, String> payload = ImmutableMap.of("key", "value");
        final Message message = Message.builder()
                .body(objectMapper.writeValueAsString(payload))
                .messageId("messageId")
                .build();
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl("queueUrl").build();
        final MethodParameter messagePayloadParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object payloadArgument = service.resolveArgument(queueProperties, messagePayloadParameter, message);

        // assert
        assertThat(payloadArgument).isEqualTo(payload);
    }


    @Test
    public void shouldBeAbleToResolveMessageIdParameters() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class, String.class, String.class);
        final Map<String, String> payload = ImmutableMap.of("key", "value");
        final Message message = Message.builder()
                .body(objectMapper.writeValueAsString(payload))
                .messageId("messageId")
                .build();
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl("queueUrl").build();
        final MethodParameter messageIdParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[1])
                .parameterIndex(1)
                .build();

        // act
        final Object messageIdArgument = service.resolveArgument(queueProperties, messageIdParameter, message);

        // assert
        assertThat(messageIdArgument).isEqualTo("messageId");
    }

    @Test
    public void shouldBeAbleToResolveMessageAttributeParameters() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class, String.class, String.class);
        final Map<String, String> payload = ImmutableMap.of("key", "value");
        final Message message = Message.builder()
                .body(objectMapper.writeValueAsString(payload))
                .messageId("messageId")
                .messageAttributes(ImmutableMap.of(
                        "key", MessageAttributeValue.builder()
                                .dataType(MessageAttributeDataTypes.STRING.getValue())
                                .stringValue("test")
                                .build()
                ))
                .build();
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl("queueUrl").build();
        final MethodParameter messagePayloadParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[2])
                .parameterIndex(2)
                .build();

        // act
        final Object payloadArgument = service.resolveArgument(queueProperties, messagePayloadParameter, message);

        // assert
        assertThat(payloadArgument).isEqualTo("test");
    }

    @Test
    public void shouldBeAbleToResolveMessageSystemAttributeParameters() throws Exception {
        // arrange
        final Method method = CoreArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class, String.class, String.class);
        final Map<String, String> payload = ImmutableMap.of("key", "value");
        final Message message = Message.builder()
                .body(objectMapper.writeValueAsString(payload))
                .messageId("messageId")
                .attributes(ImmutableMap.of(MessageSystemAttributeName.SEQUENCE_NUMBER, "test"))
                .build();
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl("queueUrl").build();
        final MethodParameter messagePayloadParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[3])
                .parameterIndex(3)
                .build();

        // act
        final Object payloadArgument = service.resolveArgument(queueProperties, messagePayloadParameter, message);

        // assert
        assertThat(payloadArgument).isEqualTo("test");
    }

    @SuppressWarnings( {"unused" })
    public void method(@Payload final Map<String, String> payload,
                       @MessageId final String messageId,
                       @MessageAttribute("key") final String attribute,
                       @MessageSystemAttribute(MessageSystemAttributeName.SEQUENCE_NUMBER) final String sequenceNumber) {

    }
}
