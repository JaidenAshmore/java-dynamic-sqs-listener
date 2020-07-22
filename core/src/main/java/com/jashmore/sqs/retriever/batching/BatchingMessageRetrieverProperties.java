package com.jashmore.sqs.retriever.batching;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.aws.AwsConstants;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;

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
     * <p>Note that this trigger size may not be reached due to the the waiting time going higher than {@link #getBatchingPeriod()}, in
     * this case it will just request as many messages as threads requesting messages.
     *
     * <p>This number should be smaller than the maximum number of messages that can be downloaded from AWS as it doesn't make much sense to have a batch size
     * greater than this value.
     *
     * @return the number of threads to be requesting messages for the retrieval of messages background thread to be triggered
     */
    @Positive
    @Max(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS)
    int getBatchSize();

    /**
     * The maximum period of time that the background thread will wait for the number of threads waiting for messages to reach
     * {@link #getBatchSize()} before requesting messages regardless of this count.
     *
     * <p>Note that the background thread threads will ignore this period if the current number of threads requesting messages goes over
     * the {@link #getBatchSize()} limit.
     *
     * <p>This value must be greater than zero as it does not make sense for it to be negative. It is also recommended not to have this as a
     * very small time duration as a small duration will result in a constant looping of the buffering thread.
     *
     * @return the polling period between attempts to get messages
     */
    @Nullable
    @Positive
    Duration getBatchingPeriod();

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
    Duration getMessageVisibilityTimeout();

    /**
     * The amount of time that the background thread for receiving messages should sleep after an error is thrown.
     *
     * <p>This is needed to stop the background thread from constantly requesting for more messages which constantly throwing errors. For example, maybe the
     * connection to the SQS throws a 403 or some other error and we don't want to be constantly retrying to make the connection unnecessarily. This
     * therefore sleeps the thread for this period before attempting again.
     *
     * <p>If this value is null or negative, {@link BatchingMessageRetrieverConstants#DEFAULT_BACKOFF_TIME} will be used as the backoff period.
     *
     * @return the number of milliseconds to sleep the thread after an error is thrown
     */
    @Nullable
    @PositiveOrZero
    Duration getErrorBackoffTime();
}
