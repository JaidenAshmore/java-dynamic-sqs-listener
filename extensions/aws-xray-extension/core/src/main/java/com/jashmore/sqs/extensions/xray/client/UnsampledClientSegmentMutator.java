package com.jashmore.sqs.extensions.xray.client;

import com.amazonaws.xray.entities.Segment;

/**
 * {@link ClientSegmentMutator} that will set the segment as being unsampled.
 *
 * <p>This is useful if you don't wish to track the SQS information in the library, such as the message retriever.
 */
public class UnsampledClientSegmentMutator implements ClientSegmentMutator {
    @Override
    public void mutateSegment(final Segment segment) {
        segment.setSampled(false);
    }
}
