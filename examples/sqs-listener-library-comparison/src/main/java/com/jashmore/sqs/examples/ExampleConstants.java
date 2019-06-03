package com.jashmore.sqs.examples;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExampleConstants {
    /**
     * The number of messages to place onto the queue before beginning to process the messages.
     */
    public static final int NUMBER_OF_MESSAGES = 1_000;

    /**
     * The amount of time that the thread will be slept while processing the message. This will represent IO.
     */
    public static final long MESSAGE_IO_TIME_IN_MS = 100;

    /**
     * The amount of time it takes to get a message from the remote SQS queue.
     */
    public static final long MESSAGE_RETRIEVAL_LATENCY_IN_MS = 100;

}
