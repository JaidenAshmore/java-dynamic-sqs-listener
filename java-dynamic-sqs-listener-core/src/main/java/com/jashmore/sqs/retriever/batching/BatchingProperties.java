package com.jashmore.sqs.retriever.batching;

import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * The properties used to configure the {@link BatchingMessageRetriever}.
 *
 * <p>Note that while only a {@link StaticBatchingProperties} is provided by the core implementation, the implementors of this may be dynamic
 * in how they provide these values. Note that there may be a delay between when this value changes in the source system compared to when the
 * {@link BatchingMessageRetriever} starts to use it based on the internal implementation of the {@link BatchingMessageRetriever}.
 */
public interface BatchingProperties {
    /**
     * The period of time between attempts to get messages from the SQS queue.
     *
     * <p>This background thread for the {@link BatchingMessageRetriever} will wait for this time period before seeing how many threads are requesting messages
     * and do a single call out to obtain as many messages as possible.
     *
     * <p>Note that this value will be ignored if the current number of threads requesting messages goes over the {@link #getNumberOfThreadsWaitingTrigger()}
     * limit. E.g. each new thread that requests a message will check the total number of threads requesting messages and if it greater than
     * {@link #getNumberOfThreadsWaitingTrigger()} the background thread will be notified to obtain the messages.
     *
     * @return the polling period in ms between attempts to get messages
     */
    int getMessageRetrievalPollingPeriodInMs();

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
     * @return the number of threads to be requesting messages for the retrieval of messages background thread to be triggered
     */
    int getNumberOfThreadsWaitingTrigger();

    /**
     * The visibility timeout for the message.
     *
     * <p>E.g. the number of seconds that a message can be kept before it is assumed that it wasn't completed and will be put back onto the queue
     *
     * @return the visibility timeout for the message
     * @see ReceiveMessageRequest#visibilityTimeout for where this is applied against
     */
    int getVisibilityTimeoutInSeconds();
}
