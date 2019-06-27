package com.jashmore.sqs.retriever.batching;

import com.jashmore.sqs.aws.AwsConstants;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import javax.annotation.Nullable;
import javax.validation.constraints.Max;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

/**
 * The properties used to configure the {@link BatchingMessageRetriever} which will be continually polled throughout the execution and therefore the
 * values can change dynamically during runtime.
 */
public interface BatchingMessageRetrieverProperties {
    /**
     * The total number of threads requesting messages that will result in the the background thread to actually request the messages.
     *
     * <p>This is useful if you want to only obtain messages in certain batches instead of always requesting them when a thread needs one. E.g. you have
     * 6 threads all processing messages and once all 6 are requesting messages you want it to request 6 messages. If there are more threads requesting
     * messages than this value, the {@link BatchingMessageRetriever} will retrieve as many messages as possible and therefore this number does not
     * represent the batch size of the message request but just the trigger point for when it should retrieve messages.
     *
     * <p>This limit will be checked every time a new thread requests a message so it should be implemented in a performant manner, either by being non
     * CPU or IO intensive or by implementing caching.
     *
     * <p>Note that this trigger size may not be reached due to the the waiting time going higher than {@link #getMessageRetrievalPollingPeriodInMs()}, in
     * this case it will just request as many messages as threads requesting messages.
     *
     * <p>This number should be smaller than the maximum number of messages that can be downloaded from AWS as it doesn't make much sense to have a batch size
     * greater than this value.
     *
     * @return the number of threads to be requesting messages for the retrieval of messages background thread to be triggered
     */
    @Positive
    @Max(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS)
    int getNumberOfThreadsWaitingTrigger();

    /**
     * The maximum period of time that the background thread will wait for the number of threads waiting for messages to reach
     * {@link #getNumberOfThreadsWaitingTrigger()} before requesting messages regardless of this count.
     *
     * <p>Note that the background thread threads will ignore this period if the current number of threads requesting messages goes over
     * the {@link #getNumberOfThreadsWaitingTrigger()} limit.
     *
     * <p>If this number is zero, there will be no polling period and the background thread will wait indefinitely for the
     * {@link #getNumberOfThreadsWaitingTrigger()} to be reached. If this value is null, the value will defaulted to zero and a warning will be logged
     * indicating that this value should be specifically set.
     *
     * @return the polling period in milliseconds between attempts to get messages
     */
    @Nullable
    @PositiveOrZero
    Long getMessageRetrievalPollingPeriodInMs();

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
     * <p>If this value is null, negative or zero, {@link BatchingMessageRetrieverConstants#DEFAULT_BACKOFF_TIME_IN_MS} will be used as the backoff period.
     *
     * @return the number of milliseconds to sleep the thread after an error is thrown
     */
    @Nullable
    @PositiveOrZero
    Long getErrorBackoffTimeInMilliseconds();
}
