package com.jashmore.sqs.retriever.prefetch;

import lombok.experimental.UtilityClass;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@UtilityClass
public class PrefetchingMessageRetrieverConstants {
    /**
     * The default time to wait for messages from SQS in seconds before giving up.
     *
     * <p>Note in this scenario another request will be tried again.
     *
     * @see ReceiveMessageRequest#waitTimeSeconds for where this is applied against
     */
    public static final int DEFAULT_WAIT_TIME_FOR_MESSAGES_FROM_SQS_IN_SECONDS = 30;

    /**
     * A default time for the visibility of the message to be processed.
     *
     * @see ReceiveMessageRequest#visibilityTimeout for where this is applied against
     */
    public static final int DEFAULT_MESSAGE_VISIBILITY_TIMEOUT = 30;

    /**
     * The default backoff timeout for when there is an error retrieving messages.
     */
    public static final int DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS = 10_000;
}
