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
    private final String sqsServerUrl;

    /**
     * Details about the queues for the server which will be attempted to be created on connection.
     */
    @Singular
    private final List<QueueConfig> queues;

    /**
     * Contains configuration for a single queue.
     */
    @Value
    @Builder
    public static class QueueConfig {

        /**
         * The name of the queue.
         */
        private final String queueName;

        /**
         * The amount of time messages should be visible before putting back onto the queue.
         */
        private final Integer visibilityTimeout;

        /**
         * The amount of times that a message can be retrieved before it should be plcaed into the Dead Letter Queue.
         *
         * <p>If this value is non null a dead letter queue will be created and a redrive policy linked to it with this max receive count. If this value is null
         * no dead letter queue will be created and attached.
         */
        private final Integer maxReceiveCount;
    }
}
