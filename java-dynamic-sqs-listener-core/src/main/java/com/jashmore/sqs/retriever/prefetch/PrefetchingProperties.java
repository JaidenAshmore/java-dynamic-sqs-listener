package com.jashmore.sqs.retriever.prefetch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS;
import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;

import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import lombok.Builder;
import lombok.Value;

import java.util.Optional;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Value
@Builder(toBuilder = true)
public class PrefetchingProperties {
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
    public static final int DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS = 2000;

    /**
     * The minimum number of messages that should be queued before it will stopWithChildrenThreadsInterrupted requesting more messages.
     *
     * <p>For example if the desiredMinPrefetchedMessages = 5, maxPrefetchedMessages = 10 and the current prefetched messages is 6 it will not call out to AWS
     * for more messages. However, once the current prefetched messages goes below 5 it will request more messages.
     *
     * <p>Constraints on this field include:
     * <ul>
     *     <li>This value must not be null</li>
     *     <li>This value must be greater than or equal to 0</li>
     *     <li>This value must be less than {@link #maxPrefetchedMessages}</li>
     * </ul>
     */
    @Min(0)
    @NotNull
    private final Integer desiredMinPrefetchedMessages;

    /**
     * The total number of messages that can be pulled from the server and not currently being processed.
     *
     * <p>Constraints on this field include:
     * <ul>
     *     <li>This value must not be null</li>
     *     <li>This value must be greater than 0</li>
     *     <li>This value must be greater than than {@link #desiredMinPrefetchedMessages}</li>
     * </ul>
     */
    @Min(1)
    @NotNull
    private final Integer maxPrefetchedMessages;

    /**
     * The number of messages that can be pulled down from the queue in one request.
     *
     * <p>This may not be the actual number of messages that will be returned as it is limited by SQS, currently a maximum of 10 and the number of currently
     * prefetched messages.
     *
     * @see ReceiveMessageRequest#maxNumberOfMessages for where this is applied against
     */
    @Size(min = 1, max = MAX_NUMBER_OF_MESSAGES_FROM_SQS)
    private final Integer maxNumberOfMessagesToObtainFromServer;

    /**
     * The maximum number of seconds to wait for messages to be obtained from AWS.
     *
     * @see ReceiveMessageRequest#waitTimeSeconds for where this is applied against
     */
    @Size(max = MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
    private final Integer maxWaitTimeInSecondsToObtainMessagesFromServer;

    /**
     * The visibility timeout for the message.
     *
     * <p>E.g. the number of seconds that a message can be kept before it is assumed that it wasn't completed and will be put back onto the queue
     *
     * @see ReceiveMessageRequest#visibilityTimeout for where this is applied against
     */
    private final Integer visibilityTimeoutForMessagesInSeconds;

    /**
     * If there was an error retrieving a message from the remote server, the retriever will backoff and try again after this many milliseconds.
     *
     * <p>This prevents constant cycling of this thread that achieves nothing.
     */
    private final Integer errorBackoffTimeInMilliseconds;

    /**
     * Constructor used to validate all of the fields, see each individual field documentation for constraints.
     */
    public PrefetchingProperties(final Integer desiredMinPrefetchedMessages,
                                 final Integer maxPrefetchedMessages,
                                 final Integer maxNumberOfMessagesToObtainFromServer,
                                 final Integer maxWaitTimeInSecondsToObtainMessagesFromServer,
                                 final Integer visibilityTimeoutForMessagesInSeconds,
                                 final Integer errorBackoffTimeInMilliseconds) {
        checkArgument(desiredMinPrefetchedMessages >= 0, "desiredMinPrefetchedMessages should be greater than equal to zero");
        checkArgument(maxPrefetchedMessages > 0, "maxPrefetchedMessages should be greater than equal to zero");
        checkArgument(maxNumberOfMessagesToObtainFromServer == null || maxNumberOfMessagesToObtainFromServer > 0,
                "maxNumberOfMessagesToObtainFromServer should be greater than 0");
        checkArgument(errorBackoffTimeInMilliseconds == null || errorBackoffTimeInMilliseconds >= 0,
                "errorBackoffTimeInMilliseconds should be greater than or equal to zero");
        checkArgument(maxWaitTimeInSecondsToObtainMessagesFromServer == null || maxWaitTimeInSecondsToObtainMessagesFromServer >= 0,
                "maxWaitTimeInSecondsToObtainMessagesFromServer should be greater than or equal to zero");

        checkArgument(desiredMinPrefetchedMessages <= maxPrefetchedMessages,
                "maxPrefetchedMessages(" + maxPrefetchedMessages + ") should be greater than or equal to "
                        + "desiredMinPrefetchedMessages(" + desiredMinPrefetchedMessages + ")");
        checkArgument(maxNumberOfMessagesToObtainFromServer == null || maxNumberOfMessagesToObtainFromServer <= MAX_NUMBER_OF_MESSAGES_FROM_SQS,
                "maxNumberOfMessagesToObtainFromServer should be less than the SQS limit of " + MAX_NUMBER_OF_MESSAGES_FROM_SQS);
        checkArgument(maxWaitTimeInSecondsToObtainMessagesFromServer == null
                        || maxWaitTimeInSecondsToObtainMessagesFromServer <= MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS,
                "maxWaitTimeInSecondsToObtainMessagesFromServer should be less than the SQS limit of " + MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);

        this.desiredMinPrefetchedMessages = desiredMinPrefetchedMessages;
        this.maxPrefetchedMessages = maxPrefetchedMessages;
        this.maxNumberOfMessagesToObtainFromServer = Optional.ofNullable(maxNumberOfMessagesToObtainFromServer)
                .orElse(MAX_NUMBER_OF_MESSAGES_FROM_SQS);
        this.maxWaitTimeInSecondsToObtainMessagesFromServer = Optional.ofNullable(maxWaitTimeInSecondsToObtainMessagesFromServer)
                .orElse(DEFAULT_WAIT_TIME_FOR_MESSAGES_FROM_SQS_IN_SECONDS);
        this.visibilityTimeoutForMessagesInSeconds = Optional.ofNullable(visibilityTimeoutForMessagesInSeconds)
                .orElse(DEFAULT_MESSAGE_VISIBILITY_TIMEOUT);
        this.errorBackoffTimeInMilliseconds = Optional.ofNullable(errorBackoffTimeInMilliseconds)
                .orElse(DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS);
    }
}
