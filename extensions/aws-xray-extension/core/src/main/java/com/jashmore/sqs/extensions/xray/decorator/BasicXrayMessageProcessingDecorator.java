package com.jashmore.sqs.extensions.xray.decorator;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.TraceHeader;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

/**
 * A very basic decorator that will start a new segment and link this to any existing Xray trace in the message.
 * 
 * <p>See <a href="https://docs.aws.amazon.com/xray/latest/devguide/xray-services-sqs.html">Xray SQS Services</a>
 */
public class BasicXrayMessageProcessingDecorator implements MessageProcessingDecorator {
    private final AWSXRayRecorder recorder;
    private final DecoratorSegmentNamingStrategy segmentNamingStrategy;

    /**
     * Constructor.
     *
     * @param segmentNamingStrategy the strategy for naming the new segments
     */
    public BasicXrayMessageProcessingDecorator(final DecoratorSegmentNamingStrategy segmentNamingStrategy) {
        this.recorder = AWSXRay.getGlobalRecorder();
        this.segmentNamingStrategy = segmentNamingStrategy;
    }

    /**
     * Constructor.
     *
     * @param recorder              the recorder used to start and stop segments
     * @param segmentNamingStrategy the strategy for naming the new segments
     */
    public BasicXrayMessageProcessingDecorator(final AWSXRayRecorder recorder,
                                               final DecoratorSegmentNamingStrategy segmentNamingStrategy) {
        this.recorder = recorder;
        this.segmentNamingStrategy = segmentNamingStrategy;
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
    }

    @Override
    public void onMessageProcessingThreadComplete(MessageProcessingContext context, Message message) {
        recorder.endSegment();
    }
}
