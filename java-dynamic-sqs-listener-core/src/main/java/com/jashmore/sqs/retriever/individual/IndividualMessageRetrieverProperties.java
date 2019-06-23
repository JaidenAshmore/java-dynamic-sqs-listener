package com.jashmore.sqs.retriever.individual;

import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import javax.annotation.Nullable;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

public interface IndividualMessageRetrieverProperties {
    /**
     * Represents the time that messages received from the SQS queue should be invisible from other consumers of the queue before it is considered a failure
     * and placed onto the queue for future retrieval.
     *
     * <p>If a null or non-positive number is returned than no visibility timeout will be submitted for this message and therefore the default visibility
     * set on the SQS queue will be used.
     *
     * @return the visibility timeout for the message
     * @see ReceiveMessageRequest#visibilityTimeout() for where this is applied against
     */
    @Nullable
    @Positive
    Integer getMessageVisibilityTimeoutInSeconds();

    /**
     * The number of milliseconds that the background thread for receiving messages should sleep after an error is thrown.
     *
     * <p>This is needed to stop the background thread from constantly requesting for more messages which constantly throwing errors. For example, maybe the
     * connection to the SQS throws a 403 or some other error and we don't want to be constantly retrying to make the connection unnecessarily. This
     * therefore sleeps the thread for this period before attempting again.
     *
     * <p>If this value is null, negative or zero, {@link IndividualMessageRetrieverConstants#DEFAULT_BACKOFF_TIME_IN_MS} will be used as the backoff period.
     *
     * @return the number of milliseconds to sleep the thread after an error is thrown
     */
    @Nullable
    @PositiveOrZero
    Long getErrorBackoffTimeInMilliseconds();
}
