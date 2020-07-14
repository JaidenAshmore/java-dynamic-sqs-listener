package com.jashmore.sqs.extensions.xray.decorator;

import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Mutator that can be used to apply your own custom changes to the segment.
 *
 * <p>Implementations of this mutator must be thread safe as multiple threads can be using this concurrently.
 */
@FunctionalInterface
public interface DecoratorSubsegmentMutator {
    /**
     * Given the {@link Segment} created for the {@link BasicXrayMessageProcessingDecorator}, apply any desired changes, e.g. setting as unsampled, etc.
     *
     * @param subsegment the subsegment to mutate
     * @param context    the context for this message
     * @param message    the message being processed
     */
    void mutateSubsegment(Subsegment subsegment, MessageProcessingContext context, Message message);
}
