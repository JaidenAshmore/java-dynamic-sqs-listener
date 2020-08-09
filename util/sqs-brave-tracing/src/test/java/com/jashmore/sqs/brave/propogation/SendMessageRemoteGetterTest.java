package com.jashmore.sqs.brave.propogation;

import static brave.Span.Kind.PRODUCER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.utils.ImmutableMap;

class SendMessageRemoteGetterTest {

    @Test
    void spanKindIsProducer() {
        assertThat(new SendMessageRemoteGetter().spanKind()).isEqualTo(PRODUCER);
    }

    @Test
    void willObtainStringValueWhenFound() {
        final Map<String, MessageAttributeValue> attributes = ImmutableMap.of(
            "key",
            MessageAttributeValue.builder().dataType("String").stringValue("value").build()
        );

        assertThat(new SendMessageRemoteGetter().get(attributes, "key")).isEqualTo("value");
    }

    @Test
    void willReturnNullWhenNotFound() {
        final Map<String, MessageAttributeValue> attributes = ImmutableMap.of(
            "another",
            MessageAttributeValue.builder().dataType("String").stringValue("value").build()
        );

        assertThat(new SendMessageRemoteGetter().get(attributes, "key")).isNull();
    }
}
