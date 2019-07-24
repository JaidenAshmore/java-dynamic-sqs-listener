package com.jashmore.sqs.util;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.scaladsl.Http;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.concurrent.TimeUnit;

class LocalSqsAsyncClientTest {
    private String queueServerUrl;

    @BeforeEach
    void setUp() {
        final SQSRestServer sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        queueServerUrl = "http://localhost:" + serverBinding.localAddress().getPort();
    }

    @Test
    void whenBuildingLocalSqsAsyncClientQueuesAndDlqsWillBeCreatedAutomatically() throws Exception {
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
    void whenDeadLetterQueueIsBuiltItIsLinkedToCorrespondingQueue() throws Exception {
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
    void whenMaxReceiveCountUsedAndNoDeadLetterQueueNameIsIncludedTheDefaultNameIsused() throws Exception {
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
    void maxReceiveCountIsIncludedInQueue() throws Exception {
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
    void visibilityTimeoutIsIncludedInQueueWhenBuilt() throws Exception {
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

    @Test
    void sendingMessagesToLocalQueueViaNameWillSendMessagesToCorrectQueue() throws Exception {
        // arrange
        final SqsQueuesConfig queuesConfig = SqsQueuesConfig.builder()
                .sqsServerUrl(queueServerUrl)
                .queue(SqsQueuesConfig.QueueConfig.builder()
                        .queueName("queueName")
                        .visibilityTimeout(60)
                        .build())
                .build();
        final LocalSqsAsyncClient sqsAsyncClient = new LocalSqsAsyncClient(queuesConfig);
        sqsAsyncClient.buildQueues();

        // act
        sqsAsyncClient.sendMessageToLocalQueue("queueName", SendMessageRequest.builder().messageBody("payload").build()).get(30, TimeUnit.SECONDS);
        sqsAsyncClient.sendMessageToLocalQueue("queueName", "payload2").get(30, TimeUnit.SECONDS);
        sqsAsyncClient.sendMessageToLocalQueue("queueName", (builder) -> builder.messageBody("payload3")).get(30, TimeUnit.SECONDS);

        // assert
        final String approximateNumberOfMessages = sqsAsyncClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueServerUrl + "/queue/queueName")
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build())
                .thenApply(response -> response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                .get(30, TimeUnit.SECONDS);
        assertThat(approximateNumberOfMessages).isEqualTo("3");
    }

    @Test
    void queueNameCanBeUsedToGetQueueUrl() {
        // arrange
        final SqsQueuesConfig queuesConfig = SqsQueuesConfig.builder()
                .sqsServerUrl(queueServerUrl)
                .queue(SqsQueuesConfig.QueueConfig.builder()
                        .queueName("queueName")
                        .visibilityTimeout(60)
                        .build())
                .build();
        final LocalSqsAsyncClient sqsAsyncClient = new LocalSqsAsyncClient(queuesConfig);
        sqsAsyncClient.buildQueues();

        // act
        final String queueUrl = sqsAsyncClient.getQueueUrl("queueName");

        // assert
        assertThat(queueUrl).isEqualTo(queueServerUrl + "/queue/queueName");
    }
}
