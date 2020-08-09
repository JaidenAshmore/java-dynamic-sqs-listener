package com.jashmore.sqs.argument.messageid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;

class MessageIdArgumentResolverTest {
    private final MessageIdArgumentResolver messageIdArgumentResolver = new MessageIdArgumentResolver();

    @Test
    void messageIdCanBeResolvedFromMessage() {
        // arrange
        final Message message = Message.builder().messageId("id").build();

        // act
        final Object argument = messageIdArgumentResolver.resolveArgumentForParameter(null, null, message);

        // assert
        assertThat(argument).isEqualTo("id");
    }
}
