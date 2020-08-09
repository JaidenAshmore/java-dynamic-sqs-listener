package com.jashmore.sqs.brave.propogation;

import static brave.Span.Kind.PRODUCER;

import brave.Span;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.util.Map;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * Used to set the tracing information into the message attributes of a SQS message.
 *
 * @see SendMessageRemoteGetter for extraction this information from the message attributes
 */
public class SendMessageRemoteSetter implements Propagation.RemoteSetter<Map<String, MessageAttributeValue>> {

    @Override
    public Span.Kind spanKind() {
        return PRODUCER;
    }

    @Override
    public void put(final Map<String, MessageAttributeValue> carrier, final String fieldName, final String value) {
        carrier.put(fieldName, MessageAttributeValue.builder().dataType("String").stringValue(value).build());
    }

    /**
     * Helper static function to create an injector for this {@link Propagation.RemoteGetter}.
     *
     * @param tracing trace instrumentation utilities
     * @return the injector
     */
    public static TraceContext.Injector<Map<String, MessageAttributeValue>> create(final Tracing tracing) {
        return tracing.propagation().injector(new SendMessageRemoteSetter());
    }
}
