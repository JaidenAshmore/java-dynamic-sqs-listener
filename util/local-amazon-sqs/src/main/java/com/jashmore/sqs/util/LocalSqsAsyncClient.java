package com.jashmore.sqs.util;

import static com.jashmore.sqs.util.SqsQueuesConfig.DEFAULT_SQS_SERVER_URL;
import static com.jashmore.sqs.util.SqsQueuesConfig.QueueConfig.DEFAULT_MAX_RECEIVE_COUNT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.utils.SdkAutoCloseable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;

/**
 * Helper implementation of the {@link SqsAsyncClient} that can be used to connect to a locally running SQS server.
 *
 * <p>The implementation is kept to an absolute minimum and it will only connect to the server and set up any queues that should be set up on initial
 * connection. For any further configuration options look at creating your own implementation.
 */
@SuppressWarnings("unused")
@Slf4j
public class LocalSqsAsyncClient implements SqsAsyncClient {
    private final SqsQueuesConfig sqsQueuesConfig;

    @Delegate(excludes = SdkAutoCloseable.class)
    private final SqsAsyncClient delegate;

    private Map<String, String> queueUrlMap;

    public LocalSqsAsyncClient(final SqsQueuesConfig sqsQueuesConfig) {
        this.sqsQueuesConfig = sqsQueuesConfig;
        final String sqsServerUrl = Optional.ofNullable(sqsQueuesConfig.getSqsServerUrl())
                .orElse(DEFAULT_SQS_SERVER_URL);
        log.info("Connecting to local SQS service at {}", sqsServerUrl);

        try {
            delegate = SqsAsyncClient.builder()
                    .endpointOverride(new URI(sqsServerUrl))
                    .region(Region.of("localstack"))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKeyId", "secretAccessKey")))
                    .build();
        } catch (URISyntaxException uriSyntaxException) {
            throw new RuntimeException("Error building local SQS Client", uriSyntaxException);
        }
    }

    /**
     * Send the message content to a local queue with the given name.
     *
     * @param queueName   name of the queue to send the message to
     * @param messageBody the contents of the message
     * @return the response for sending the messages as a {@link CompletableFuture}
     */
    public CompletableFuture<SendMessageResponse> sendMessageToLocalQueue(final String queueName, final String messageBody) {
        return sendMessageToLocalQueue(queueName, builder -> builder.messageBody(messageBody));
    }

    /**
     * Send the following message request object to the local queue with the given name.
     *
     * @param queueName          name of the queue to send the message to
     * @param sendMessageRequest the request to send to the local queue
     * @return the response for sending the messages as a {@link CompletableFuture}
     */
    public CompletableFuture<SendMessageResponse> sendMessageToLocalQueue(final String queueName, final SendMessageRequest sendMessageRequest) {
        return sendMessage(sendMessageRequest.toBuilder()
                .queueUrl(queueUrlMap.get(queueName))
                .build());
    }

    /**
     * Send a message to a local queue with the given name.
     *
     * @param queueName                         name of the queue to send the message to
     * @param sendMessageRequestBuilderConsumer a consumer of the request builder that can be used to generate the request
     * @return the response for sending the messages as a {@link CompletableFuture}
     */
    public CompletableFuture<SendMessageResponse> sendMessageToLocalQueue(final String queueName,
                                                                          final Consumer<SendMessageRequest.Builder> sendMessageRequestBuilderConsumer) {
        return sendMessage(builder -> {
            sendMessageRequestBuilderConsumer.accept(builder);
            builder.queueUrl(queueUrlMap.get(queueName));
        });
    }

    /**
     * Get the queue URL of one of the local queues with the given name.
     *
     * @param queueName the name of the queue to get the URL for
     * @return the URL of the queue with the provided name, if it exists
     */
    public String getQueueUrl(final String queueName) {
        return queueUrlMap.get(queueName);
    }

    /**
     * Build all of the queues that are required for this local client once the bean has been created.
     */
    @PostConstruct
    public void buildQueues() {
        queueUrlMap = Maps.newHashMap();
        for (final SqsQueuesConfig.QueueConfig queueConfig : sqsQueuesConfig.getQueues()) {

            log.debug("Creating local queue: {}", queueConfig.getQueueName());
            final CreateQueueRequest.Builder createQueueRequestBuilder = CreateQueueRequest
                    .builder()
                    .queueName(queueConfig.getQueueName());
            ImmutableMap.Builder<QueueAttributeName, String> attributesBuilder = ImmutableMap.builder();
            if (queueConfig.getVisibilityTimeout() != null) {
                attributesBuilder.put(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(queueConfig.getVisibilityTimeout()));
            }

            if (queueConfig.getMaxReceiveCount() != null || queueConfig.getDeadLetterQueueName() != null) {
                final String deadLetterQueueName = Optional.ofNullable(queueConfig.getDeadLetterQueueName()).orElse(queueConfig.getQueueName() + "-dlq");
                final int maxReceiveCount = Optional.ofNullable(queueConfig.getMaxReceiveCount())
                        .orElse(DEFAULT_MAX_RECEIVE_COUNT);
                final String deadLetterQueueArn = createDeadLetterQueue(deadLetterQueueName);
                attributesBuilder.put(
                        QueueAttributeName.REDRIVE_POLICY,
                        String.format("{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"%d\"}", deadLetterQueueArn, maxReceiveCount)
                );
            }

            createQueueRequestBuilder.attributes(attributesBuilder.build());

            try {
                final CreateQueueResponse createQueueResponse = delegate.createQueue(createQueueRequestBuilder.build()).get();
                queueUrlMap.put(queueConfig.getQueueName(), createQueueResponse.queueUrl());
            } catch (InterruptedException | ExecutionException exception) {
                throw new RuntimeException("Error creating queues", exception);
            }
        }
    }

    /**
     * Create a Dead Letter Queue that should be used for the queue with the provided name.
     *
     * @param queueName the name of the queue that this dead letter queue is for
     * @return the queue ARN of the dead letter queue created
     */
    private String createDeadLetterQueue(final String queueName) {
        try {
            log.debug("Creating dead letter queue: {}", queueName);
            return delegate.createQueue((builder -> builder.queueName(queueName)))
                    .thenCompose(createQueueResponse -> delegate.getQueueAttributes(builder -> builder
                            .queueUrl(createQueueResponse.queueUrl())
                            .attributeNames(QueueAttributeName.QUEUE_ARN))
                    )
                    .thenApply(queueAttributes -> queueAttributes.attributes().get(QueueAttributeName.QUEUE_ARN))
                    .get();
        } catch (InterruptedException | ExecutionException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void close() {
        log.info("Closing local SDK");
    }
}
