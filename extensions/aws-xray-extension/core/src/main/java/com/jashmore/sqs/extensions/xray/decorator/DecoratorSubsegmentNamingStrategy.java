package com.jashmore.sqs.extensions.xray.decorator;

import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Strategy for naming the Subsegment that will be started when processing a message.
 *
 * <p>Implementations of this strategy must be thread safe as multiple threads can be using this concurrently.
 */
@ThreadSafe
@FunctionalInterface
public interface DecoratorSubsegmentNamingStrategy {
    /**
     * Get the name of the subsegment for this message being processed.
     *
     * @param context the context of the message listener
     * @param message the message being processed
     * @return the name of the segment
     */
    String getSubsegmentName(MessageProcessingContext context, Message message);
}
