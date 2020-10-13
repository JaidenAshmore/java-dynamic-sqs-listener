package com.jashmore.sqs.extensions.xray.decorator;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceHeader;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import lombok.Builder;
import lombok.Value;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

/**
 * A very basic decorator that will start a new segment and link this to any existing Xray trace in the message.
 *
 * <p>See <a href="https://docs.aws.amazon.com/xray/latest/devguide/xray-services-sqs.html">Xray SQS Services</a>
 */
public class BasicXrayMessageProcessingDecorator implements MessageProcessingDecorator {

    private static final String SEGMENT_CONTEXT_ATTRIBUTE_NAME = BasicXrayMessageProcessingDecorator.class.getSimpleName() + ":segment";
    private static final String SUBSEGMENT_CONTEXT_ATTRIBUTE_NAME =
        BasicXrayMessageProcessingDecorator.class.getSimpleName() + ":subsegment";
    private static final String DEFAULT_SEGMENT_NAME = "message-listener";

    private final AWSXRayRecorder recorder;
    private final DecoratorSegmentNamingStrategy segmentNamingStrategy;
    private final DecoratorSegmentMutator segmentMutator;
    private final DecoratorSubsegmentNamingStrategy subsegmentNamingStrategy;
    private final DecoratorSubsegmentMutator subsegmentMutator;
    private final boolean generateSubsegment;

    public BasicXrayMessageProcessingDecorator(final Options options) {
        this.recorder = options.recorder != null ? options.recorder : AWSXRay.getGlobalRecorder();
        this.segmentNamingStrategy =
            options.segmentNamingStrategy != null
                ? options.segmentNamingStrategy
                : new StaticDecoratorSegmentNamingStrategy(DEFAULT_SEGMENT_NAME);
        this.segmentMutator = options.segmentMutator;
        this.subsegmentNamingStrategy =
            options.subsegmentNamingStrategy != null
                ? options.subsegmentNamingStrategy
                : (context, message) -> context.getListenerIdentifier();
        this.subsegmentMutator = options.subsegmentMutator;
        this.generateSubsegment = options.generateSubsegment != null ? options.generateSubsegment : true;
    }

    @Override
    public void onPreMessageProcessing(final MessageProcessingContext context, final Message message) {
        final Segment segment = recorder.beginSegment(segmentNamingStrategy.getSegmentName(context, message));
        final String rawTraceHeader = message.attributes().get(MessageSystemAttributeName.AWS_TRACE_HEADER);
        if (rawTraceHeader != null) {
            final TraceHeader traceHeader = TraceHeader.fromString(rawTraceHeader);
            segment.setTraceId(traceHeader.getRootTraceId());
            segment.setParentId(traceHeader.getParentId());
            segment.setSampled(traceHeader.getSampled().equals(TraceHeader.SampleDecision.SAMPLED));
        }
        if (segmentMutator != null) {
            segmentMutator.mutateSegment(segment, context, message);
        }
        context.setAttribute(SEGMENT_CONTEXT_ATTRIBUTE_NAME, segment);

        if (generateSubsegment) {
            final Subsegment subsegment = recorder.beginSubsegment(subsegmentNamingStrategy.getSubsegmentName(context, message));
            if (subsegmentMutator != null) {
                subsegmentMutator.mutateSubsegment(subsegment, context, message);
            }
            context.setAttribute(SUBSEGMENT_CONTEXT_ATTRIBUTE_NAME, subsegment);
            recorder.setTraceEntity(subsegment);
        }
    }

    @Override
    public void onMessageProcessingFailure(final MessageProcessingContext context, final Message message, final Throwable throwable) {
        final Subsegment subsegment = context.getAttribute(SUBSEGMENT_CONTEXT_ATTRIBUTE_NAME);
        if (subsegment != null) {
            recorder.setTraceEntity(subsegment);
            subsegment.addException(throwable);
            recorder.endSubsegment();
        }

        final Segment segment = context.getAttribute(SEGMENT_CONTEXT_ATTRIBUTE_NAME);
        if (segment != null) {
            recorder.setTraceEntity(segment);
            segment.addException(throwable);
            recorder.endSegment();
        }
        recorder.clearTraceEntity();
    }

    @Override
    public void onMessageProcessingSuccess(final MessageProcessingContext context, final Message message, @Nullable final Object object) {
        final Subsegment subsegment = context.getAttribute(SUBSEGMENT_CONTEXT_ATTRIBUTE_NAME);
        if (subsegment != null) {
            recorder.setTraceEntity(subsegment);
            recorder.endSubsegment();
        }

        final Segment segment = context.getAttribute(SEGMENT_CONTEXT_ATTRIBUTE_NAME);
        if (segment != null) {
            recorder.setTraceEntity(segment);
            recorder.endSegment();
        }
        recorder.clearTraceEntity();
    }

    @Override
    public void onMessageProcessingThreadComplete(final MessageProcessingContext context, final Message message) {
        recorder.clearTraceEntity();
    }

    @Value
    @Builder
    public static class Options {

        /**
         * The Xray recorder that will be used to start the segments and subsegments for the message listeners.
         *
         * <p>If this is not set, the {@link AWSXRay#getGlobalRecorder()} will be used.
         */
        AWSXRayRecorder recorder;
        /**
         * Strategy for naming the Segment that will be started when processing a message.
         *
         * <p>If this is not set, all segments will have the name {@link #DEFAULT_SEGMENT_NAME}.
         */
        DecoratorSegmentNamingStrategy segmentNamingStrategy;
        /**
         * Optional mutator that can be used to add custom configuration for the segment started.
         */
        DecoratorSegmentMutator segmentMutator;

        /**
         * Whether the message listener should generate a subsegment before executing.
         *
         * <p>If null, this will default to true.
         */
        @Builder.Default
        Boolean generateSubsegment = true;

        /**
         * Strategy for naming the Subsegment that will be started when processing a message.
         *
         * <p>If this is not set, all subsegments will have the same name as the message listener identifier.
         */
        DecoratorSubsegmentNamingStrategy subsegmentNamingStrategy;
        /**
         * Optional mutator that can be used to add custom configuration for the subsegment started.
         */
        DecoratorSubsegmentMutator subsegmentMutator;
    }
}
