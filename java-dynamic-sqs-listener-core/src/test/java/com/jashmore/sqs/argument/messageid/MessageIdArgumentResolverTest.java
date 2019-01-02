package com.jashmore.sqs.argument.messageid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

public class MessageIdArgumentResolverTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private final MessageIdArgumentResolver messageIdArgumentResolver = new MessageIdArgumentResolver();

    @Test
    public void name() {
        // arrange
        final Message message = Message.builder().messageId("id").build();

        // act
        final Object argument = messageIdArgumentResolver.resolveArgumentForParameter(null, null, message);

        // assert
        assertThat(argument).isEqualTo("id");
    }
}