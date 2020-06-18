package com.jashmore.sqs.extensions.brave.decorator;

import brave.Span;
import brave.Tracing;
import brave.propagation.ThreadLocalSpan;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import com.jashmore.sqs.brave.propogation.SendMessageRemoteGetter;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import lombok.Builder;
import lombok.Value;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Message processing decorator that is used to add Brave tracing to the message processing thread.
 *
 * <p>If the SQS message contains Brave attributes they will be extracted and the trace will be continued from
 * here, otherwise a new trace will be started.
 */
@ParametersAreNonnullByDefault
public class BraveMessageProcessingDecorator implements MessageProcessingDecorator {
    private static final BiFunction<MessageProcessingContext, Message, String> DEFAULT_SPAN_NAME_CREATOR = (details, message)
            -> "sqs-listener-" + details.getListenerIdentifier();

    private final ThreadLocalSpan threadLocalSpan;
    private final TraceContext.Extractor<Map<String, MessageAttributeValue>> traceExtractor;
    private final BiFunction<MessageProcessingContext, Message, String> spanNameCreator;

    public BraveMessageProcessingDecorator(final Tracing tracing) {
        this(tracing, Options.builder().build());
    }

    public BraveMessageProcessingDecorator(final Tracing tracing, final Options options) {
        this.threadLocalSpan = ThreadLocalSpan.create(tracing.tracer());
        this.traceExtractor = options.traceExtractor != null ? options.traceExtractor : SendMessageRemoteGetter.create(tracing);
        this.spanNameCreator = options.spanNameCreator != null ? options.spanNameCreator : DEFAULT_SPAN_NAME_CREATOR;
    }

    @Override
    public void onPreSupply(final MessageProcessingContext context, final Message message) {
        final TraceContextOrSamplingFlags traceContextOrSamplingFlags = traceExtractor.extract(message.messageAttributes());
        final Span span = threadLocalSpan.next(traceContextOrSamplingFlags);
        if (span != null && !span.isNoop()) {
            span
                    .name(spanNameCreator.apply(context, message))
                    .kind(Span.Kind.CONSUMER)
                    .start();
        }
    }

    @Override
    public void onSupplyFailure(MessageProcessingContext context, Message message, Throwable throwable) {
        final Span span = threadLocalSpan.remove();
        if (span != null) {
            span.error(throwable);
            span.finish();
        }
    }

    @Override
    public void onSupplySuccess(MessageProcessingContext context, Message message) {
        final Span span = threadLocalSpan.remove();
        if (span != null) {
            span.finish();
        }
    }

    @Value
    @Builder
    public static class Options {
        TraceContext.Extractor<Map<String, MessageAttributeValue>> traceExtractor;
        BiFunction<MessageProcessingContext, Message, String> spanNameCreator;
    }
}
