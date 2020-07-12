package com.jashmore.sqs.extensions.xray.client;

import com.jashmore.documentation.annotations.ThreadSafe;

/**
 * Strategy for naming the Segment that will be created if non exist when using the {@link XrayWrappedSqsAsyncClient}.
 *
 * <p>Implementations of this strategy must be thread safe as multiple threads can be using this concurrently.
 */
@ThreadSafe
@FunctionalInterface
public interface ClientSegmentNamingStrategy {
    /**
     * Get the name of the segment.
     *
     * @return the segment name
     */
    String getSegmentName();
}
