package com.jashmore.sqs.retriever.prefetch;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;

import com.jashmore.sqs.aws.AwsConstants;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import javax.validation.constraints.Max;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

public interface PrefetchingMessageRetrieverProperties {
    /**
     * The minimum number of messages that should be queued before it will stopWithChildrenThreadsInterrupted requesting more messages.
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
     *     <li>This value must be greater than or equal to 0</li>
     *     <li>This value must be less than {@link #getMaxPrefetchedMessages()}</li>
     * </ul>
     *
     * @return the minimum number of prefetched messages
     */
    @PositiveOrZero
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
     *     <li>This value must be greater than 0</li>
     *     <li>This value must be greater than than {@link #getDesiredMinPrefetchedMessages()}</li>
     * </ul>
     *
     * @return the maximum number of prefetched messages
     */
    @Positive
    int getMaxPrefetchedMessages();

    /**
     * The number of seconds that the request for messages will wait for a message to be available on the queue.
     *
     * <p>Note that this will only wait until at least one message is received and it will not wait until the total number of requested messages is available.
     *
     * <p>If this value is null, the {@link AwsConstants#MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS} will be used when requesting messages from SQS.
     *
     * @return the wait time in seconds for obtaining messages
     * @see ReceiveMessageRequest#waitTimeSeconds for the usage
     */
    @Positive
    @Max(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
    Integer getMaxWaitTimeInSecondsToObtainMessagesFromServer();

    /**
     * The visibility timeout for the message.
     *
     * <p>E.g. the number of seconds that a message can be kept before it is assumed that it wasn't completed and will be put back onto the queue
     *
     * @see ReceiveMessageRequest#visibilityTimeout for where this is applied against
     *
     * @return the visibility timeout for messages where null means to use the SQS default visibility timeout
     */
    Integer getVisibilityTimeoutForMessagesInSeconds();

    /**
     * If there was an error retrieving a message from the remote server, the retriever will backoff and try again after this many milliseconds, which
     * prevents constant cycling of this thread that achieves nothing.
     *
     * <p>If this value is null, negative or zero, {@link PrefetchingMessageRetrieverConstants#DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS} will be used
     * as the backoff period.
     *
     * @return the backoff time in milliseconds or null if the default backoff time should be used
     */
    Integer getErrorBackoffTimeInMilliseconds();
}
