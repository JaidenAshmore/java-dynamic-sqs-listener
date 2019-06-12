package com.jashmore.sqs.util;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.scaladsl.Http;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

public class LocalSqsAsyncClientTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private String queueServerUrl;

    @Before
    public void setUp() {
        final SQSRestServer sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        queueServerUrl = "http://localhost:" + serverBinding.localAddress().getPort();
    }

    @Test
    public void whenBuildingLocalSqsAsyncClientQueuesAndDlqsWillBeCreatedAutomatically() throws Exception {
        // arrange
        final SqsQueuesConfig queuesConfig = SqsQueuesConfig.builder()
                .sqsServerUrl(queueServerUrl)
                .queue(SqsQueuesConfig.QueueConfig.builder()
                        .queueName("queueName")
                        .deadLetterQueueName("queueNameDlq")
                        .build())
                .queue(SqsQueuesConfig.QueueConfig.builder()
                        .queueName("queueName2")
                        .deadLetterQueueName("queueName2Dlq")
                        .build())
                .build();
        final LocalSqsAsyncClient sqsAsyncClient = new LocalSqsAsyncClient(queuesConfig);

        // act
        sqsAsyncClient.buildQueues();

        // assert
        final ListQueuesResponse listQueuesResponse = sqsAsyncClient.listQueues().get();
        assertThat(listQueuesResponse.queueUrls()).hasSize(4);
    }

    @Test
    public void whenDeadLetterQueueIsBuiltItIsLinkedToCorrespondingQueue() throws Exception {
        // arrange
        final SqsQueuesConfig queuesConfig = SqsQueuesConfig.builder()
                .sqsServerUrl(queueServerUrl)
                .queue(SqsQueuesConfig.QueueConfig.builder()
                        .queueName("queueName")
                        .deadLetterQueueName("queueNameDlq")
                        .build())
                .build();
        final LocalSqsAsyncClient sqsAsyncClient = new LocalSqsAsyncClient(queuesConfig);

        // act
        sqsAsyncClient.buildQueues();

        // assert
        final String reDrivePolicy = sqsAsyncClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueServerUrl + "/queue/queueName")
                .attributeNames(QueueAttributeName.REDRIVE_POLICY)
                .build())
                .thenApply(getQueueAttributesResponse -> getQueueAttributesResponse.attributes().get(QueueAttributeName.REDRIVE_POLICY))
                .get();
        assertThat(reDrivePolicy).contains("queueNameDlq");
    }

    @Test
    public void whenMaxReceiveCountUsedAndNoDeadLetterQueueNameIsIncludedTheDefaultNameIsused() throws Exception {
        // arrange
        final SqsQueuesConfig queuesConfig = SqsQueuesConfig.builder()
                .sqsServerUrl(queueServerUrl)
                .queue(SqsQueuesConfig.QueueConfig.builder()
                        .queueName("queueName")
                        .maxReceiveCount(3)
                        .build())
                .build();
        final LocalSqsAsyncClient sqsAsyncClient = new LocalSqsAsyncClient(queuesConfig);

        // act
        sqsAsyncClient.buildQueues();

        // assert
        final ListQueuesResponse listQueuesResponse = sqsAsyncClient.listQueues().get();
        assertThat(listQueuesResponse.queueUrls()).hasSize(2);
        assertThat(listQueuesResponse.queueUrls()).contains(queueServerUrl + "/queue/queueName-dlq");
    }

    @Test
    public void maxReceiveCountIsIncludedInQueue() throws Exception {
        // arrange
        final SqsQueuesConfig queuesConfig = SqsQueuesConfig.builder()
                .sqsServerUrl(queueServerUrl)
                .queue(SqsQueuesConfig.QueueConfig.builder()
                        .queueName("queueName")
                        .maxReceiveCount(3)
                        .build())
                .build();
        final LocalSqsAsyncClient sqsAsyncClient = new LocalSqsAsyncClient(queuesConfig);

        // act
        sqsAsyncClient.buildQueues();

        // assert
        final String reDrivePolicy = sqsAsyncClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueServerUrl + "/queue/queueName")
                .attributeNames(QueueAttributeName.REDRIVE_POLICY)
                .build())
                .thenApply(getQueueAttributesResponse -> getQueueAttributesResponse.attributes().get(QueueAttributeName.REDRIVE_POLICY))
                .get();
        assertThat(reDrivePolicy).contains("\"maxReceiveCount\":3");
    }

    @Test
    public void visibilityTimeoutIsIncludedInQueueWhenBuilt() throws Exception {
        // arrange
        final SqsQueuesConfig queuesConfig = SqsQueuesConfig.builder()
                .sqsServerUrl(queueServerUrl)
                .queue(SqsQueuesConfig.QueueConfig.builder()
                        .queueName("queueName")
                        .visibilityTimeout(60)
                        .build())
                .build();
        final LocalSqsAsyncClient sqsAsyncClient = new LocalSqsAsyncClient(queuesConfig);

        // act
        sqsAsyncClient.buildQueues();

        // assert
        final String visibilityTimeout = sqsAsyncClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueServerUrl + "/queue/queueName")
                .attributeNames(QueueAttributeName.VISIBILITY_TIMEOUT)
                .build())
                .thenApply(getQueueAttributesResponse -> getQueueAttributesResponse.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT))
                .get();
        assertThat(visibilityTimeout).contains("60");
    }
}
