package com.jashmore.sqs.util;

import static com.jashmore.sqs.util.SqsQueuesConfig.DEFAULT_SQS_SERVER_URL;
import static com.jashmore.sqs.util.SqsQueuesConfig.QueueConfig.DEFAULT_MAX_RECEIVE_COUNT;
import static java.util.stream.Collectors.toList;
import static software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES;
import static software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE;

import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.PurgeQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.utils.SdkAutoCloseable;

/**
 * Helper implementation of the {@link SqsAsyncClient} that can be used to connect to a locally running SQS server.
 *
 * <p>The implementation is kept to an absolute minimum and it will only connect to the server and set up any queues that should be set up on initial
 * connection. For any further configuration options look at creating your own implementation.
 */
@Slf4j
public class LocalSqsAsyncClientImpl implements LocalSqsAsyncClient {

    @Delegate(excludes = SdkAutoCloseable.class)
    private final SqsAsyncClient delegate;

    public LocalSqsAsyncClientImpl(final SqsQueuesConfig sqsQueuesConfig) {
        this(sqsQueuesConfig, builder -> {});
    }

    public LocalSqsAsyncClientImpl(final SqsQueuesConfig sqsQueuesConfig, final Consumer<SqsAsyncClientBuilder> clientBuilderConsumer) {
        final String serverUrl = Optional.ofNullable(sqsQueuesConfig.getSqsServerUrl()).orElse(DEFAULT_SQS_SERVER_URL);
        log.info("Connecting to local SQS service at {}", serverUrl);

        final URI serverUri;
        try {
            serverUri = new URI(serverUrl);
        } catch (URISyntaxException uriSyntaxException) {
            throw new RuntimeException("Invalid Server URL for SQS Server", uriSyntaxException);
        }

        final SqsAsyncClientBuilder clientBuilder = SqsAsyncClient
            .builder()
            .endpointOverride(serverUri)
            .region(Region.of("localstack"))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKeyId", "secretAccessKey")));
        clientBuilderConsumer.accept(clientBuilder);
        delegate = clientBuilder.build();

        if (!sqsQueuesConfig.getQueues().isEmpty()) {
            try {
                buildQueues(delegate, sqsQueuesConfig).get();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted waiting for queues to be built", interruptedException);
            } catch (ExecutionException executionException) {
                throw new RuntimeException("Error building initial queues", executionException.getCause());
            }
        }
    }

    @SuppressWarnings("unused")
    public LocalSqsAsyncClientImpl(final SqsAsyncClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<SendMessageResponse> sendMessage(final String queueName, final String messageBody) {
        return sendMessage(queueName, builder -> builder.messageBody(messageBody));
    }

    @Override
    public CompletableFuture<SendMessageResponse> sendMessage(final String queueName, final SendMessageRequest sendMessageRequest) {
        return getQueueUrl(builder -> builder.queueName(queueName))
            .thenApply(GetQueueUrlResponse::queueUrl)
            .thenCompose(queueUrl -> sendMessage(sendMessageRequest.toBuilder().queueUrl(queueUrl).build()));
    }

    @Override
    public CompletableFuture<SendMessageResponse> sendMessage(
        final String queueName,
        final Consumer<SendMessageRequest.Builder> sendMessageRequestBuilderConsumer
    ) {
        return getQueueUrl(builder -> builder.queueName(queueName))
            .thenApply(GetQueueUrlResponse::queueUrl)
            .thenCompose(queueUrl ->
                sendMessage(builder -> {
                    sendMessageRequestBuilderConsumer.accept(builder);
                    builder.queueUrl(queueUrl);
                })
            );
    }

    @Override
    public CompletableFuture<CreateRandomQueueResponse> createRandomQueue() {
        final String queueName = UUID.randomUUID().toString().replace("-", "");

        log.info("Creating queue with name: {}", queueName);
        return createQueue(requestBuilder -> requestBuilder.queueName(queueName).build())
            .thenApply(createQueueResponse -> CreateRandomQueueResponse.builder().response(createQueueResponse).queueName(queueName).build()
            );
    }

    @Override
    public CompletableFuture<CreateRandomQueueResponse> createRandomFifoQueue() {
        return createRandomFifoQueue(builder -> {});
    }

    @Override
    public CompletableFuture<CreateRandomQueueResponse> createRandomFifoQueue(
        Consumer<CreateQueueRequest.Builder> sendMessageRequestBuilderConsumer
    ) {
        final String queueName = UUID.randomUUID().toString().replace("-", "") + ".fifo";

        log.info("Creating FIFO queue with name: {}", queueName);
        return createQueue(requestBuilder -> {
                sendMessageRequestBuilderConsumer.accept(requestBuilder);
                final Map<QueueAttributeName, String> originalAttributes = requestBuilder.build().attributes();
                final Map<QueueAttributeName, String> actualAttributes = new HashMap<>(originalAttributes);
                actualAttributes.putIfAbsent(QueueAttributeName.FIFO_QUEUE, String.valueOf(true));
                actualAttributes.putIfAbsent(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, String.valueOf(false));
                requestBuilder.queueName(queueName).attributes(actualAttributes).build();
            })
            .thenApply(createQueueResponse -> CreateRandomQueueResponse.builder().response(createQueueResponse).queueName(queueName).build()
            );
    }

    @Override
    public CompletableFuture<List<PurgeQueueResponse>> purgeAllQueues() {
        return delegate
            .listQueues()
            .thenApply(ListQueuesResponse::queueUrls)
            .thenCompose(queueUrls ->
                CompletableFutureUtils.allOf(queueUrls.stream().map(url -> purgeQueue(builder -> builder.queueUrl(url))).collect(toList()))
            );
    }

    @Override
    public CompletableFuture<Integer> getApproximateMessages(final String queueName) {
        return getQueueUrl(builder -> builder.queueName(queueName))
            .thenApply(GetQueueUrlResponse::queueUrl)
            .thenCompose(queueUrl ->
                getQueueAttributes(builder ->
                    builder.queueUrl(queueUrl).attributeNames(APPROXIMATE_NUMBER_OF_MESSAGES, APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                )
            )
            .thenApply(GetQueueAttributesResponse::attributes)
            .thenApply(attributes ->
                Integer.parseInt(attributes.get(APPROXIMATE_NUMBER_OF_MESSAGES)) +
                Integer.parseInt(attributes.get(APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE))
            );
    }

    /**
     * Build all of the queues that are required for this local client once the bean has been created.
     */
    private static CompletableFuture<List<CreateQueueResponse>> buildQueues(SqsAsyncClient delegate, SqsQueuesConfig sqsQueuesConfig) {
        final List<CompletableFuture<CreateQueueResponse>> queueFutures = sqsQueuesConfig
            .getQueues()
            .stream()
            .map(queueConfig -> buildQueue(delegate, queueConfig))
            .collect(toList());
        return CompletableFutureUtils.allOf(queueFutures);
    }

    private static CompletableFuture<CreateQueueResponse> buildQueue(SqsAsyncClient delegate, SqsQueuesConfig.QueueConfig queueConfig) {
        log.debug("Creating local queue: {}", queueConfig.getQueueName());

        final Map<QueueAttributeName, String> attributes = new HashMap<>();
        if (queueConfig.getVisibilityTimeout() != null) {
            attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(queueConfig.getVisibilityTimeout()));
        }

        final CompletableFuture<?> createDeadLetterQueueFuture;
        if (queueConfig.getMaxReceiveCount() != null || queueConfig.getDeadLetterQueueName() != null) {
            final String deadLetterQueueName = Optional
                .ofNullable(queueConfig.getDeadLetterQueueName())
                .orElse(queueConfig.getQueueName() + "-dlq");
            final int maxReceiveCount = Optional.ofNullable(queueConfig.getMaxReceiveCount()).orElse(DEFAULT_MAX_RECEIVE_COUNT);
            createDeadLetterQueueFuture =
                createDeadLetterQueue(delegate, queueConfig, deadLetterQueueName)
                    .thenAccept(deadLetterQueueArn ->
                        attributes.put(
                            QueueAttributeName.REDRIVE_POLICY,
                            String.format(
                                "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"%d\"}",
                                deadLetterQueueArn,
                                maxReceiveCount
                            )
                        )
                    );
        } else {
            createDeadLetterQueueFuture = CompletableFuture.completedFuture(null);
        }

        if (queueConfig.isFifoQueue()) {
            if (!queueConfig.getQueueName().endsWith(".fifo")) {
                throw new IllegalArgumentException("Queue name must end in .fifo for FIFO queues");
            }
            attributes.put(QueueAttributeName.FIFO_QUEUE, String.valueOf(true));
            attributes.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, String.valueOf(false));
        }

        return createDeadLetterQueueFuture.thenCompose(ignored ->
            delegate.createQueue(builder -> builder.queueName(queueConfig.getQueueName()).attributes(attributes))
        );
    }

    /**
     * Create a Dead Letter Queue that should be used for the queue with the provided name.
     *
     * @param queueName the name of the queue that this dead letter queue is for
     * @return the future with the queue ARN of the dead letter queue created
     */
    private static CompletableFuture<String> createDeadLetterQueue(
        SqsAsyncClient delegate,
        SqsQueuesConfig.QueueConfig queueConfig,
        final String queueName
    ) {
        log.debug("Creating dead letter queue: {}", queueName);
        return delegate
            .createQueue(
                (
                    builder -> {
                        if (queueConfig.isFifoQueue()) {
                            final Map<QueueAttributeName, String> attributes = new HashMap<>();
                            attributes.put(QueueAttributeName.FIFO_QUEUE, "true");
                            attributes.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "false");
                            builder.attributes(attributes);
                        }
                        builder.queueName(queueName);
                    }
                )
            )
            .thenCompose(createQueueResponse ->
                delegate.getQueueAttributes(builder ->
                    builder.queueUrl(createQueueResponse.queueUrl()).attributeNames(QueueAttributeName.QUEUE_ARN)
                )
            )
            .thenApply(queueAttributes -> queueAttributes.attributes().get(QueueAttributeName.QUEUE_ARN));
    }

    @Override
    public void close() {}
}
