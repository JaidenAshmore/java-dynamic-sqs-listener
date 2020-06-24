package com.jashmore.sqs.retriever.prefetch;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

public interface PrefetchingMessageRetrieverProperties {
    /**
     * The minimum number of messages that should be in the queue before it will request more messages.
     *
     * <p>For example if the desiredMinPrefetchedMessages = 5, maxPrefetchedMessages = 10 and the current prefetched messages is 6 it will not call out to AWS
     * for more messages. However, once the current prefetched messages goes below 5 it will request more messages.
     *
     * <p>This value is not able to be dynamic during the execution as it impacts the underlying internal queue that stores prefetched messages. Having
     * this value allowed to be to continually change during execution would significantly complicate the implementation.  If you need to apply this dynamic
     * nature a different implementation should be written.
     *
     * <p>Constraints on this field include:
     * <ul>
     *     <li>this value must be greater than 0</li>
     *     <li>this value must be less than {@link #getMaxPrefetchedMessages()}</li>
     * </ul>
     *
     * @return the minimum number of prefetched messages
     */
    @Positive
    int getDesiredMinPrefetchedMessages();

    /**
     * The total number of messages that can be pulled from the server and not currently being processed.
     *
     * <p>This value is not able to be dynamic during the execution as it impacts the underlying internal queue that stores prefetched messages. Having
     * this value allowed to be to continually change during execution would significantly complicate the implementation.  If you need to apply this dynamic
     * nature a different implementation should be written.
     *
     * <p>Constraints on this field include:
     * <ul>
     *     <li>this value must be greater than 0</li>
     *     <li>this value must be greater than than {@link #getDesiredMinPrefetchedMessages()}</li>
     * </ul>
     *
     * @return the maximum number of prefetched messages
     */
    @Positive
    int getMaxPrefetchedMessages();

    /**
     * The visibility timeout for the message.
     *
     * <p>E.g. the number of seconds that a message can be kept before it is assumed that it wasn't completed and will be put back onto the queue
     *
     * <p>If this value is null, no visibility timeout will be set on the message retrieval.
     *
     * @return the visibility timeout for messages where null means to use the SQS default visibility timeout
     * @see ReceiveMessageRequest#visibilityTimeout() for where this is applied against
     */
    @Nullable
    @Positive
    Integer getMessageVisibilityTimeoutInSeconds();

    /**
     * If there was an error retrieving a message from the remote server, the retriever will backoff and try again after this many milliseconds, which
     * prevents constant cycling of this thread that achieves nothing.
     *
     * <p>If this value is null or negative, {@link PrefetchingMessageRetrieverConstants#DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS} will be used
     * as the backoff period.
     *
     * @return the backoff time in milliseconds or null if the default backoff time should be used
     */
    @Nullable
    @PositiveOrZero
    Integer getErrorBackoffTimeInMilliseconds();
}
