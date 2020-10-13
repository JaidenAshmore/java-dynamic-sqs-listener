package com.jashmore.sqs.extensions.xray.client;

/**
 * A {@link ClientSegmentNamingStrategy} that just returns the same segment name whenever it is requested.
 */
public class StaticClientSegmentNamingStrategy implements ClientSegmentNamingStrategy {

    private final String segmentName;

    /**
     * Constructor.
     *
     * @param segmentName the name of the segment to be used each time
     */
    public StaticClientSegmentNamingStrategy(final String segmentName) {
        this.segmentName = segmentName;
    }

    @Override
    public String getSegmentName() {
        return segmentName;
    }
}
