package com.jashmore.sqs.extensions.xray.decorator;

import com.jashmore.sqs.decorator.MessageProcessingContext;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Static implementation that will return the same segment name regardless of the message listener or message being processed.
 */
public class StaticDecoratorSegmentNamingStrategy implements DecoratorSegmentNamingStrategy {
    private final String segmentName;

    /**
     * Constructor.
     *
     * @param segmentName the name of the segment to always use
     */
    public StaticDecoratorSegmentNamingStrategy(final String segmentName) {
        this.segmentName = segmentName;
    }

    @Override
    public String getSegmentName(final MessageProcessingContext context, final Message message) {
        return segmentName;
    }
}
