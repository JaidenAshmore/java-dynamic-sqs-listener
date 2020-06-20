package com.jashmore.sqs.brave.propogation;

import static brave.Span.Kind.PRODUCER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.HashMap;
import java.util.Map;

class SendMessageRemoteSetterTest {

    @Test
    void spanKindIsProducer() {
        assertThat(new SendMessageRemoteSetter().spanKind()).isEqualTo(PRODUCER);
    }

    @Test
    void willSetStringValueWhenFound() {
        final Map<String, MessageAttributeValue> attributes = new HashMap<>();

        new SendMessageRemoteSetter().put(attributes, "other", "value");
        assertThat(attributes).containsEntry("other", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue("value")
                .build());
    }
}