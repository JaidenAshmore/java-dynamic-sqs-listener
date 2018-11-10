package com.jashmore.sqs.retriever.individual;

import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

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
