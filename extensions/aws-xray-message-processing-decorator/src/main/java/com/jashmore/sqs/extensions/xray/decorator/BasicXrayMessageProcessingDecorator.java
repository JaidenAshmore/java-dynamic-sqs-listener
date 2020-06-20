package com.jashmore.sqs.extensions.xray.decorator;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import software.amazon.awssdk.services.sqs.model.Message;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A very basic decorator that just starts and stops an XRay segment.
 */
@ParametersAreNonnullByDefault
public class BasicXrayMessageProcessingDecorator implements MessageProcessingDecorator {
    private final AWSXRayRecorder recorder;

    public BasicXrayMessageProcessingDecorator() {
        this.recorder = AWSXRay.getGlobalRecorder();
    }

    public BasicXrayMessageProcessingDecorator(final AWSXRayRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void onPreMessageProcessing(final MessageProcessingContext context, final Message message) {
        recorder.beginSegment("sqs-listener-" + context.getListenerIdentifier());
    }

    @Override
    public void onMessageProcessingThreadComplete(MessageProcessingContext context, Message message) {
        recorder.endSegment();
    }
}
