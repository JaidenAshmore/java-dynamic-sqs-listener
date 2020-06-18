package com.jashmore.sqs.brave.propogation;

import static brave.Span.Kind.PRODUCER;

import brave.Span;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.Map;
import java.util.Optional;

/**
 * Used to consume the tracing information from the message attributes of the SQS message.
 *
 * @see SendMessageRemoteSetter for placing this information into the message attributes
 */
public class SendMessageRemoteGetter implements Propagation.RemoteGetter<Map<String, MessageAttributeValue>> {

    @Override
    public Span.Kind spanKind() {
        return PRODUCER;
    }

    @Override
    public String get(final Map<String, MessageAttributeValue> request, final String fieldName) {
        return Optional.ofNullable(request.get(fieldName))
                .map(MessageAttributeValue::stringValue)
                .orElse(null);
    }

    /**
     * Helper static function to create an extractor for this {@link Propagation.RemoteGetter}.
     *
     * @param tracing trace instrumentation utilities
     * @return the extractor
     */
    public static TraceContext.Extractor<Map<String, MessageAttributeValue>> create(final Tracing tracing) {
        return tracing.propagation().extractor(new SendMessageRemoteGetter());
    }
}
