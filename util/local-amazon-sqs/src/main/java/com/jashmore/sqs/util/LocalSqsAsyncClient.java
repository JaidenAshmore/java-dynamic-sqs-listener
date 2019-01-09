package com.jashmore.sqs.util;

import static com.jashmore.sqs.util.SqsQueuesConfig.DEFAULT_SQS_SERVER_URL;

import com.google.common.collect.ImmutableMap;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.utils.SdkAutoCloseable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;

/**
 * Helper implementation of the {@link SqsAsyncClient} that can be used to connect to a locally running SQS server.
 *
 * <p>The implementation is kept to an absolute minimum and it will only connect to the server and set up any queues that should be set up on initial
 * connection. For any further configuration options look at creating your own implementation.
 */
@Slf4j
public class LocalSqsAsyncClient implements SqsAsyncClient {
    private final SqsQueuesConfig sqsQueuesConfig;
    @Delegate(excludes = SdkAutoCloseable.class)
    private final SqsAsyncClient delegate;

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
     * Build all of the queues that are required for this local client once the bean has been created.
     */
    @PostConstruct
    private void buildQueues() {
        for (final SqsQueuesConfig.QueueConfig queueConfig : sqsQueuesConfig.getQueues()) {

            log.debug("Creating local queue: {}", queueConfig.getQueueName());
            final CreateQueueRequest.Builder createQueueRequestBuilder = CreateQueueRequest
                    .builder()
                    .queueName(queueConfig.getQueueName());
            ImmutableMap.Builder<QueueAttributeName, String> attributesBuilder = ImmutableMap.builder();
            if (queueConfig.getVisibilityTimeout() != null) {
                attributesBuilder.put(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(queueConfig.getVisibilityTimeout()));
            }

            if (queueConfig.getMaxReceiveCount() != null) {
                final String deadLetterQueueArn = createDeadLetterQueue(queueConfig.getQueueName());
                attributesBuilder.put(
                        QueueAttributeName.REDRIVE_POLICY,
                        String.format("{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"%d\"}", deadLetterQueueArn, queueConfig.getMaxReceiveCount())
                );
            }

            createQueueRequestBuilder.attributes(attributesBuilder.build());

            try {
                delegate.createQueue(createQueueRequestBuilder.build()).get();
            } catch (InterruptedException | ExecutionException exception) {
                throw new RuntimeException("Error creating queues", exception);
            }
        }
    }

    /**
     * Create a Dead Letter Queue that should be used for the queue with the provided name.
     *
     * <p>This will create a queue with "-dlq" appended to the original queue's name.
     *
     * @param queueName the name of the queue that this dead letter queue is for
     * @return the queue ARN of the dead letter queue created
     */
    private String createDeadLetterQueue(final String queueName) {
        try {
            log.debug("Creating local queue: {}-dlq", queueName);
            return delegate.createQueue((builder -> builder.queueName(queueName + "-dlq")))
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
