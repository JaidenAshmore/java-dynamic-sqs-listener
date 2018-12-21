package com.jashmore.sqs.util;

import static com.jashmore.sqs.util.SqsQueuesConfig.DEFAULT_SQS_SERVER_URL;

import com.google.common.collect.ImmutableMap;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Helper implementation of the {@link AmazonSQSAsync} that can be used to connect to a locally running SQS server.
 *
 * <p>The implementation is kept to an absolute minimum and it will only connect to the server and set up any queues that should be set up on initial
 * connection. For any further configuration options look at creating your own implementation.
 */
@Slf4j
public class LocalAmazonSqsAsync implements AmazonSQSAsync {
    @Delegate
    private final AmazonSQSAsync delegate;

    public LocalAmazonSqsAsync(final SqsQueuesConfig sqsQueuesConfig) {
        final String sqsServerUrl = Optional.ofNullable(sqsQueuesConfig.getSqsServerUrl())
                .orElse(DEFAULT_SQS_SERVER_URL);
        log.info("Connecting to local SQS service at {}", sqsServerUrl);

        delegate = AmazonSQSAsyncClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(sqsServerUrl, "local"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
                .build();

        for (final SqsQueuesConfig.QueueConfig queueConfig : sqsQueuesConfig.getQueues()) {
            log.debug("Creating local queue: {}", queueConfig.getQueueName());
            final CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueConfig.getQueueName());
            if (queueConfig.getVisibilityTimeout() != null) {
                createQueueRequest
                        .withAttributes(ImmutableMap.of(QueueAttributeName.VisibilityTimeout.toString(), String.valueOf(queueConfig.getVisibilityTimeout())));
            }
            delegate.createQueue(createQueueRequest);
        }
    }
}
