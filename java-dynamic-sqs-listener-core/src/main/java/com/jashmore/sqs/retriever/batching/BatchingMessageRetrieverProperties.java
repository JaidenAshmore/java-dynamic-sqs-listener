package com.jashmore.sqs.retriever.batching;

import com.jashmore.sqs.aws.AwsConstants;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import javax.annotation.Nullable;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

/**
 * The properties used to configure the {@link BatchingMessageRetriever} which will be continually polled throughout the execution and therefore the
 * values can change dynamically during runtime.
 */
public interface BatchingMessageRetrieverProperties {
    /**
     * The limit of the number of threads that are requesting messages before the background thread will be notified to request the messages.
     *
     * <p>This is useful if you want to allow for the obtaining of messages as soon as the number of threads requesting messages hits a limit. E.g. you have
     * 6 threads all processing messages and once all 6 are requesting messages you want it to request messages now instead of waiting for the
     * {@link #getMessageRetrievalPollingPeriodInMs()} limit to be hit.
     *
     * <p>This limit will be checked every time a new thread requests a message so if this value changes after there will be no way to trigger the background
     * thread until a new thread comes to request a message.
     *
     * <p>If there are more threads requesting messages than this value, the {@link BatchingMessageRetriever} will retrieve as many messages as possible and
     * therefore this number does not represent the batch size of the message request but just the trigger point for when it should retrieve messages.
     *
     * @return the number of threads to be requesting messages for the retrieval of messages background thread to be triggered
     */
    @Positive
    int getNumberOfThreadsWaitingTrigger();

    /**
     * The maximum period of time between attempts to get messages from the SQS queue.
     *
     * <p>This background thread for the {@link BatchingMessageRetriever} will wait for this time period before seeing how many threads are requesting messages
     * and do a single call out to obtain as many messages as possible.
     *
     * <p>Note that this value will be ignored if the current number of threads requesting messages goes over the {@link #getNumberOfThreadsWaitingTrigger()}
     * limit. E.g. each new thread that requests a message will check the total number of threads requesting messages and if it greater than
     * {@link #getNumberOfThreadsWaitingTrigger()} the background thread will be notified to obtain the messages.
     *
     * <p>If this number is zero, there will be no polling period and the background thread will wait indefinitely for the
     * {@link #getNumberOfThreadsWaitingTrigger()} to be reached.
     *
     * <p>If this value is null, the value will defaulted to zero and a warning will be logged indicating that this value should be specificalyl set
     *
     * @return the polling period in ms between attempts to get messages
     */
    @PositiveOrZero
    Integer getMessageRetrievalPollingPeriodInMs();

    /**
     * The amount of time that the message has been not visible to other consumers for messages that have been obtained from the
     * {@link BatchingMessageRetriever}.
     *
     * <p>This is the number of seconds that a message can be kept before it is assumed that it wasn't completed and will be put back onto the queue.
     *
     * @return the visibility timeout for the message
     * @see ReceiveMessageRequest#visibilityTimeout for where this is applied against
     */
    @Positive
    int getVisibilityTimeoutInSeconds();

    /**
     * The number of seconds that the request for messages will wait for a message to be obtained from the queue.
     *
     * <p>If this value is null, the {@link AwsConstants#MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS} will be used when requesting messages from SQS.
     *
     * @return the wait time in seconds for obtaining messages
     * @see ReceiveMessageRequest#waitTimeSeconds
     */
    @Nullable
    @PositiveOrZero
    Integer getMessageWaitTimeInSeconds();
}
