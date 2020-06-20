package com.jashmore.sqs.extensions.brave.decorator;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
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
import javax.annotation.Nullable;
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

    private final Tracer tracer;
    private final TraceContext.Extractor<Map<String, MessageAttributeValue>> traceExtractor;
    private final BiFunction<MessageProcessingContext, Message, String> spanNameCreator;

    public BraveMessageProcessingDecorator(final Tracing tracing) {
        this(tracing, Options.builder().build());
    }

    public BraveMessageProcessingDecorator(final Tracing tracing, final Options options) {
        this.tracer = tracing.tracer();
        this.traceExtractor = options.getTraceExtractor() != null ? options.getTraceExtractor() : SendMessageRemoteGetter.create(tracing);
        this.spanNameCreator = options.getSpanNameCreator() != null ? options.getSpanNameCreator() : DEFAULT_SPAN_NAME_CREATOR;
    }

    @Override
    public void onPreMessageProcessing(MessageProcessingContext context, Message message) {
        // create span
        // start span in scope
        final TraceContextOrSamplingFlags traceContextOrSamplingFlags = traceExtractor.extract(message.messageAttributes());
        final Span span = tracer.nextSpan(traceContextOrSamplingFlags);
        if (span != null && !span.isNoop()) {
            span
                    .name(spanNameCreator.apply(context, message))
                    .kind(Span.Kind.CONSUMER)
                    .start();
            context.setAttribute(this.getClass().getSimpleName() + ":span", span);
        }
        final Tracer.SpanInScope spanInScope = tracer.withSpanInScope(span);
        context.setAttribute(this.getClass().getSimpleName() + ":span-in-scope", spanInScope);
    }

    @Override
    public void onMessageProcessingFailure(MessageProcessingContext context, Message message, Throwable throwable) {
        final Span span = context.getAttribute(this.getClass().getSimpleName() + ":span");
        if (span != null) {
            span.error(throwable)
                    .finish();
        }
    }

    @Override
    public void onMessageProcessingSuccess(MessageProcessingContext context, Message message, @Nullable Object object) {
        final Span span = context.getAttribute(this.getClass().getSimpleName() + ":span");
        if (span != null) {
            span.finish();
        }
    }

    @Override
    public void onMessageProcessingThreadComplete(MessageProcessingContext context, Message message) {
        final Tracer.SpanInScope spanInScope = context.getAttribute(this.getClass().getSimpleName() + ":span-in-scope");
        if (spanInScope != null) {
            spanInScope.close();
        }
    }

    @Value
    @Builder
    public static class Options {
        TraceContext.Extractor<Map<String, MessageAttributeValue>> traceExtractor;
        BiFunction<MessageProcessingContext, Message, String> spanNameCreator;
    }
}
