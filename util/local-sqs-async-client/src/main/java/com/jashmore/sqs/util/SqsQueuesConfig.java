package com.jashmore.sqs.util;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Configuration properties for connecting to a locally running SQS Server.
 */
@Value
@Builder
public class SqsQueuesConfig {
    /**
     * Default URL for a SQS server being built using localstack.
     */
    public static final String DEFAULT_SQS_SERVER_URL = "http://localhost:4576";

    /**
     * Optional URL for the SQS server to connect to, otherwise the {@link #DEFAULT_SQS_SERVER_URL} is used.
     */
    String sqsServerUrl;

    /**
     * Details about the queues for the server which will be attempted to be created on connection.
     */
    @Singular
    List<QueueConfig> queues;

    /**
     * Contains configuration for a single queue.
     */
    @Value
    @Builder
    public static class QueueConfig {
        public static final int DEFAULT_MAX_RECEIVE_COUNT = 3;

        /**
         * The name of the queue.
         */
        String queueName;

        /**
         * The name of the dead letter queue that should be created and linked to the queue.
         *
         * <p>If this value is null a dead letter queue will not be created. However if {@link #maxReceiveCount} is not null than a dead letter queue will
         * be created with a default name of "{queueName}-dlq".
         */
        String deadLetterQueueName;

        /**
         * The amount of time messages should be visible before putting back onto the queue.
         */
        Integer visibilityTimeout;

        /**
         * The amount of times that a message can be retrieved before it should be placed into the Dead Letter Queue.
         *
         * <p>If this value is non null a dead letter queue with a name of {@link #deadLetterQueueName} or "{queueName}-dlq" will be created and a
         * re-drive policy linked to it with this max receive count.
         */
        Integer maxReceiveCount;
    }
}
