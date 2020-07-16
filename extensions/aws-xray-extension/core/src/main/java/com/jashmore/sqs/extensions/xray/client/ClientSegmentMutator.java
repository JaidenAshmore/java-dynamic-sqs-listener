package com.jashmore.sqs.extensions.xray.client;

import com.amazonaws.xray.entities.Segment;
import com.jashmore.documentation.annotations.ThreadSafe;

/**
 * Mutator that can be used to apply your own custom changes to the segment created in the {@link XrayWrappedSqsAsyncClient}.
 *
 * <p>Implementations of this mutator must be thread safe as multiple threads can be using this concurrently.
 */
@ThreadSafe
@FunctionalInterface
public interface ClientSegmentMutator {
    /**
     * Apply changes to a segment built in the {@link XrayWrappedSqsAsyncClient}.
     *
     * @param segment the segment to configure
     */
    void mutateSegment(Segment segment);
}
