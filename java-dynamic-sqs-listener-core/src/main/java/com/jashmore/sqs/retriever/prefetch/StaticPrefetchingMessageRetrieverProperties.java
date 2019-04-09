package com.jashmore.sqs.retriever.prefetch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverConstants.DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS;
import static com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverConstants.DEFAULT_MESSAGE_VISIBILITY_TIMEOUT;
import static com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverConstants.DEFAULT_WAIT_TIME_FOR_MESSAGES_FROM_SQS_IN_SECONDS;

import lombok.Builder;

import java.util.Optional;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Builder(toBuilder = true)
public class StaticPrefetchingMessageRetrieverProperties implements PrefetchingMessageRetrieverProperties {
    private final int desiredMinPrefetchedMessages;
    private final int maxPrefetchedMessages;
    private final Integer maxWaitTimeInSecondsToObtainMessagesFromServer;
    private final Integer visibilityTimeoutForMessagesInSeconds;
    private final Integer errorBackoffTimeInMilliseconds;

    /**
     * Constructor used to validate all of the fields, see each individual field documentation for constraints.
     *
     * @param desiredMinPrefetchedMessages                   the minimum desired messages to be prefetched
     * @param maxPrefetchedMessages                          the maximum number of messages to be prefetched
     * @param maxWaitTimeInSecondsToObtainMessagesFromServer the maximum wait time for waiting for messages from the server
     * @param visibilityTimeoutForMessagesInSeconds          the visibility timeout for each message obtained from the server
     * @param errorBackoffTimeInMilliseconds                 the amount of time to backoff if an error occurred on fetching messages
     */
    public StaticPrefetchingMessageRetrieverProperties(final int desiredMinPrefetchedMessages,
                                                       final int maxPrefetchedMessages,
                                                       final Integer maxWaitTimeInSecondsToObtainMessagesFromServer,
                                                       final Integer visibilityTimeoutForMessagesInSeconds,
                                                       final Integer errorBackoffTimeInMilliseconds) {
        checkArgument(desiredMinPrefetchedMessages >= 0, "desiredMinPrefetchedMessages should be greater than equal to zero");
        checkArgument(maxPrefetchedMessages > 0, "maxPrefetchedMessages should be greater than equal to zero");
        checkArgument(errorBackoffTimeInMilliseconds == null || errorBackoffTimeInMilliseconds >= 0,
                "errorBackoffTimeInMilliseconds should be greater than or equal to zero");
        checkArgument(maxWaitTimeInSecondsToObtainMessagesFromServer == null || maxWaitTimeInSecondsToObtainMessagesFromServer >= 0,
                "maxWaitTimeInSecondsToObtainMessagesFromServer should be greater than or equal to zero");

        checkArgument(desiredMinPrefetchedMessages <= maxPrefetchedMessages,
                "maxPrefetchedMessages(" + maxPrefetchedMessages + ") should be greater than or equal to "
                        + "desiredMinPrefetchedMessages(" + desiredMinPrefetchedMessages + ")");
        checkArgument(maxWaitTimeInSecondsToObtainMessagesFromServer == null
                        || maxWaitTimeInSecondsToObtainMessagesFromServer <= MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS,
                "maxWaitTimeInSecondsToObtainMessagesFromServer should be less than the SQS limit of " + MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);

        this.desiredMinPrefetchedMessages = desiredMinPrefetchedMessages;
        this.maxPrefetchedMessages = maxPrefetchedMessages;
        this.maxWaitTimeInSecondsToObtainMessagesFromServer = Optional.ofNullable(maxWaitTimeInSecondsToObtainMessagesFromServer)
                .orElse(DEFAULT_WAIT_TIME_FOR_MESSAGES_FROM_SQS_IN_SECONDS);
        this.visibilityTimeoutForMessagesInSeconds = Optional.ofNullable(visibilityTimeoutForMessagesInSeconds)
                .orElse(DEFAULT_MESSAGE_VISIBILITY_TIMEOUT);
        this.errorBackoffTimeInMilliseconds = Optional.ofNullable(errorBackoffTimeInMilliseconds)
                .orElse(DEFAULT_ERROR_BACKOFF_TIMEOUT_IN_MILLISECONDS);
    }

    @Override
    public @Min(0) @NotNull int getDesiredMinPrefetchedMessages() {
        return desiredMinPrefetchedMessages;
    }

    @Override
    public @Min(1) @NotNull int getMaxPrefetchedMessages() {
        return maxPrefetchedMessages;
    }

    @Override
    public @Size(max = MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS) Integer getMaxWaitTimeInSecondsToObtainMessagesFromServer() {
        return maxWaitTimeInSecondsToObtainMessagesFromServer;
    }

    @Override
    public Integer getVisibilityTimeoutForMessagesInSeconds() {
        return visibilityTimeoutForMessagesInSeconds;
    }

    @Override
    public Integer getErrorBackoffTimeInMilliseconds() {
        return errorBackoffTimeInMilliseconds;
    }
}
