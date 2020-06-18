package com.jashmore.sqs.extensions.xray.decorator;

import com.amazonaws.xray.AWSXRay;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import software.amazon.awssdk.services.sqs.model.Message;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class XrayMessageProcessingDecorator implements MessageProcessingDecorator {
    @Override
    public void onPreSupply(final MessageProcessingContext context, final Message message) {
        AWSXRay.beginSegment("sqs-message-processing-" + context.getListenerIdentifier());
    }

    @Override
    public void onSupplyFinished(final MessageProcessingContext context, final Message message) {
        AWSXRay.endSegment();
    }
}
