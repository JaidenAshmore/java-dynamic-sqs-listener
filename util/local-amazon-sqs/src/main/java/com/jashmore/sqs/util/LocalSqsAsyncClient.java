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

/**
 * Helper implementation of the {@link SqsAsyncClient} that can be used to connect to a locally running SQS server.
 *
 * <p>The implementation is kept to an absolute minimum and it will only connect to the server and set up any queues that should be set up on initial
 * connection. For any further configuration options look at creating your own implementation.
 */
@Slf4j
public class LocalSqsAsyncClient implements SqsAsyncClient {
    @Delegate(excludes = SdkAutoCloseable.class)
    private final SqsAsyncClient delegate;

    public LocalSqsAsyncClient(final SqsQueuesConfig sqsQueuesConfig) {
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

        for (final SqsQueuesConfig.QueueConfig queueConfig : sqsQueuesConfig.getQueues()) {
            log.debug("Creating local queue: {}", queueConfig.getQueueName());
            final CreateQueueRequest.Builder createQueueRequestBuilder = CreateQueueRequest
                    .builder()
                    .queueName(queueConfig.getQueueName());
            if (queueConfig.getVisibilityTimeout() != null) {
                createQueueRequestBuilder
                        .attributes(ImmutableMap.of(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(queueConfig.getVisibilityTimeout())));
            }
            try {
                delegate.createQueue(createQueueRequestBuilder.build()).get();
            } catch (InterruptedException | ExecutionException exception) {
                throw new RuntimeException("Error creating queues", exception);
            }
        }
    }

    @Override
    public void close() {
        log.info("Closing local SDK");
    }
}
