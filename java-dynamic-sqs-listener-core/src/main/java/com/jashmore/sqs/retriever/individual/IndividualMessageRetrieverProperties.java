package com.jashmore.sqs.retriever.individual;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Value
@Builder
@AllArgsConstructor
public class IndividualMessageRetrieverProperties {
    /**
     * The visibility timeout for the message.
     *
     * <p>E.g. the number of seconds that a message can be kept before it is assumed that it wasn't completed and will be put back onto the queue
     *
     * @see ReceiveMessageRequest#visibilityTimeout for where this is applied against
     */
    private final Integer visibilityTimeoutForMessagesInSeconds;
}
