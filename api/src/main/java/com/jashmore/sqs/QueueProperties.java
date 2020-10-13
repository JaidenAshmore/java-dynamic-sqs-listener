package com.jashmore.sqs;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

/**
 * Contains information about the queue that will be listened against.
 */
@Value
@NonFinal
@Builder
public class QueueProperties {

    /**
     * The URL of the queue that can be used by the Amazon clients to add, remove messages etc.
     */
    String queueUrl;
}
