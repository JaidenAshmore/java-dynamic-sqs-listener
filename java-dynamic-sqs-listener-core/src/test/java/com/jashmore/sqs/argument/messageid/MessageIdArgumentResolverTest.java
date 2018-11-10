package com.jashmore.sqs.argument.messageid;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.model.Message;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MessageIdArgumentResolverTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private final MessageIdArgumentResolver messageIdArgumentResolver = new MessageIdArgumentResolver();

    @Test
    public void name() {
        // arrange
        final Message message = new Message().withMessageId("id");

        // act
        final Object argument = messageIdArgumentResolver.resolveArgumentForParameter(null, null, message);

        // assert
        assertThat(argument).isEqualTo("id");
    }
}