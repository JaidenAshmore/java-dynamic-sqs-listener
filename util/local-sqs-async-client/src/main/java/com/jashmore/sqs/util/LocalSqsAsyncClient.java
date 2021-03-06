package com.jashmore.sqs.util;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * Client that should be used for local development like integration tests or with your own localstack or ElasticMQ SQS Server.
 *
 * <p>This provides some helpful functions that makes testing easier. This should not be used in a production environment.
 */
public interface LocalSqsAsyncClient extends SqsAsyncClient {
    /**
     * Creates a random queue that can be used for testing, returning the URL for this queue.
     *
     * @return the result of creating the queue
     */
    CompletableFuture<CreateRandomQueueResponse> createRandomQueue();

    /**
     * Creates a random FIFO queue that can be used for testing, returning the result of creating the queue.
     *
     * @return the result of creating the queue
     */
    CompletableFuture<CreateRandomQueueResponse> createRandomFifoQueue();

    /**
     * Creates a random FIFO queue that can be used for testing, returning the result of creating the queue.
     *
     * @param sendMessageRequestBuilderConsumer consume the builder for adding extra configuration to the queue
     * @return the result of creating the queue
     */
    CompletableFuture<CreateRandomQueueResponse> createRandomFifoQueue(
        Consumer<CreateQueueRequest.Builder> sendMessageRequestBuilderConsumer
    );

    /**
     * Send the message content to a local queue with the given name.
     *
     * @param queueName   name of the queue to send the message to
     * @param messageBody the contents of the message
     * @return the response for sending the messages as a {@link CompletableFuture}
     */
    CompletableFuture<SendMessageResponse> sendMessage(String queueName, String messageBody);

    /**
     * Send the following message request object to the local queue with the given name.
     *
     * @param queueName          name of the queue to send the message to
     * @param sendMessageRequest the request to send to the local queue
     * @return the response for sending the messages as a {@link CompletableFuture}
     */
    CompletableFuture<SendMessageResponse> sendMessage(String queueName, SendMessageRequest sendMessageRequest);

    /**
     * Send a message to a local queue with the given name.
     *
     * @param queueName                         name of the queue to send the message to
     * @param sendMessageRequestBuilderConsumer a consumer of the request builder that can be used to generate the request
     * @return the response for sending the messages as a {@link CompletableFuture}
     */
    CompletableFuture<SendMessageResponse> sendMessage(
        String queueName,
        Consumer<SendMessageRequest.Builder> sendMessageRequestBuilderConsumer
    );

    /**
     * Purge all of the messages from all known queues.
     *
     * @return the future that will be resolved when this is completed
     */
    CompletableFuture<List<PurgeQueueResponse>> purgeAllQueues();

    /**
     * Get the approximate number of messages visible and not visible for a queue with the given URL.
     *
     * @param queueName the name of the queue
     * @return the future that will be resolved with the approximate number of messages for the queue (visible and not visible)
     */
    CompletableFuture<Integer> getApproximateMessages(String queueName);
}
