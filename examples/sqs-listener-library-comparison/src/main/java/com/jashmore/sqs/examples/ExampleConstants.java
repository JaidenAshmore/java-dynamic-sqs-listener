package com.jashmore.sqs.examples;

import static com.jashmore.sqs.examples.Queues.PREFETCHING_30_QUEUE_NAME;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExampleConstants {
    /**
     * The number of messages to place onto the queue before beginning to process the messages.
     */
    static final int NUMBER_OF_MESSAGES = 1_000;

    /**
     * The amount of time that the thread will be slept while processing the message. This will represent IO.
     */
    static final long MESSAGE_IO_TIME_IN_MS = 500;

    /**
     * The amount of time it takes to get a message from the remote SQS queue.
     */
    public static final long MESSAGE_RETRIEVAL_LATENCY_IN_MS = 200;

    /**
     * The actual queue that messages will be placed onto during the test.
     */
    static final String QUEUE_TO_TEST = PREFETCHING_30_QUEUE_NAME;
}
