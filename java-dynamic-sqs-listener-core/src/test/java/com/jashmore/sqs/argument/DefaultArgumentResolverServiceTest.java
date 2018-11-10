package com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.messageid.MessageId;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.util.Immutables;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Method;
import java.util.Map;

public class DefaultArgumentResolverServiceTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private ObjectMapper objectMapper = new ObjectMapper();

    private PayloadMapper payloadMapper = new JacksonPayloadMapper(objectMapper);

    @Mock
    private AmazonSQSAsync amazonSqsAsync;

    private DefaultArgumentResolverService service;

    @Before
    public void setUp() {
        service = new DefaultArgumentResolverService(payloadMapper, amazonSqsAsync);
    }

    @Test
    public void shouldBeAbleToResolveMethodArgumentsWithDefaultResolvers() throws Exception {
        // arrange
        final Method method = DefaultArgumentResolverServiceTest.class.getMethod("method", Map.class, String.class);
        final Map<String, String> payload = Immutables.immutableMap("key", "value");
        final Message message = new Message()
                .withBody(objectMapper.writeValueAsString(payload))
                .withMessageId("messageId");
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl("queueUrl").build();

        // act
        final Object payloadArgument = service.resolveArgument(queueProperties, method.getParameters()[0], message);
        final Object messageIdArgument = service.resolveArgument(queueProperties, method.getParameters()[1], message);

        // assert
        assertThat(payloadArgument).isEqualTo(payload);
        assertThat(messageIdArgument).isEqualTo("messageId");
    }

    @SuppressWarnings( {"unused", "WeakerAccess"})
    public void method(@Payload final Map<String, String> payload, @MessageId final String messageId) {

    }
}
