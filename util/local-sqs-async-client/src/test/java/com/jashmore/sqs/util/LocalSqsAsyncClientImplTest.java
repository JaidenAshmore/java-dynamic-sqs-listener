package com.jashmore.sqs.util;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES;

import akka.http.scaladsl.Http;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class LocalSqsAsyncClientImplTest {
    private String queueServerUrl;
    private SQSRestServer sqsRestServer;

    @BeforeEach
    void setUp() {
        sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        queueServerUrl = "http://localhost:" + serverBinding.localAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        sqsRestServer.stopAndWait();
    }

    @Test
    void passingInvalidUrlWillThrowExceptionOnCreation() {
        // act
        final RuntimeException exception = Assertions.assertThrows(RuntimeException.class, () -> new LocalSqsAsyncClientImpl(SqsQueuesConfig.builder()
                .sqsServerUrl("incorrect url")
                .build()));

        // assert
        assertThat(exception)
                .hasMessage("Invalid Server URL for SQS Server")
                .hasCauseInstanceOf(URISyntaxException.class);
    }

    @Test
    void createRandomQueueWillGenerateQueueWithRandomName() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        final LocalSqsAsyncClientImpl client = new LocalSqsAsyncClientImpl(SqsQueuesConfig.builder()
                .sqsServerUrl(queueServerUrl)
                .build());

        // act
        final CreateRandomQueueResponse firstQueueResponse = client.createRandomQueue().get(5, TimeUnit.SECONDS);
        final CreateRandomQueueResponse secondQueueResponse = client.createRandomQueue().get(5, TimeUnit.SECONDS);
        final ListQueuesResponse queues = client.listQueues().get(5, TimeUnit.SECONDS);

        // assert
        assertThat(queues.queueUrls()).hasSize(2);
        assertThat(queues.queueUrls()).contains(firstQueueResponse.queueUrl());
        assertThat(queues.queueUrls()).contains(secondQueueResponse.queueUrl());
        assertThat(firstQueueResponse.queueUrl()).isNotEqualTo(secondQueueResponse.queueUrl());
    }

    @Test
    void purgeAllQueuesWillRemoveAllMessages() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        final LocalSqsAsyncClientImpl client = new LocalSqsAsyncClientImpl(SqsQueuesConfig.builder()
                .sqsServerUrl(queueServerUrl)
                .build());
        final CreateRandomQueueResponse firstQueue = client.createRandomQueue().get(5, TimeUnit.SECONDS);
        final CreateRandomQueueResponse secondQueue = client.createRandomQueue().get(5, TimeUnit.SECONDS);
        client.sendMessage(builder -> builder.queueUrl(firstQueue.queueUrl()).messageBody("first")).get(5, TimeUnit.SECONDS);
        client.sendMessage(builder -> builder.queueUrl(secondQueue.queueUrl()).messageBody("second")).get(5, TimeUnit.SECONDS);
        assertThat(getQueueTotalMessagesVisible(client, firstQueue.getQueueName())).isEqualTo(1);
        assertThat(getQueueTotalMessagesVisible(client, secondQueue.getQueueName())).isEqualTo(1);

        // act
        client.purgeAllQueues().get(5, TimeUnit.SECONDS);

        // assert
        assertThat(getQueueTotalMessagesVisible(client, firstQueue.getQueueName())).isEqualTo(0);
        assertThat(getQueueTotalMessagesVisible(client, secondQueue.getQueueName())).isEqualTo(0);
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

        // act
        final LocalSqsAsyncClientImpl sqsAsyncClient = new LocalSqsAsyncClientImpl(queuesConfig);

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

        // act
        final LocalSqsAsyncClientImpl sqsAsyncClient = new LocalSqsAsyncClientImpl(queuesConfig);

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

        // act
        final LocalSqsAsyncClientImpl sqsAsyncClient = new LocalSqsAsyncClientImpl(queuesConfig);

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

        // act
        final LocalSqsAsyncClientImpl sqsAsyncClient = new LocalSqsAsyncClientImpl(queuesConfig);

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

        // act
        final LocalSqsAsyncClientImpl sqsAsyncClient = new LocalSqsAsyncClientImpl(queuesConfig);

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
        final LocalSqsAsyncClientImpl sqsAsyncClient = new LocalSqsAsyncClientImpl(queuesConfig);

        // act
        sqsAsyncClient.sendMessage("queueName", SendMessageRequest.builder().messageBody("payload").build()).get(30, TimeUnit.SECONDS);
        sqsAsyncClient.sendMessage("queueName", "payload2").get(30, TimeUnit.SECONDS);
        sqsAsyncClient.sendMessage("queueName", (builder) -> builder.messageBody("payload3")).get(30, TimeUnit.SECONDS);

        // assert
        final String approximateNumberOfMessages = sqsAsyncClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueServerUrl + "/queue/queueName")
                .attributeNames(APPROXIMATE_NUMBER_OF_MESSAGES)
                .build())
                .thenApply(response -> response.attributes().get(APPROXIMATE_NUMBER_OF_MESSAGES))
                .get(30, TimeUnit.SECONDS);
        assertThat(approximateNumberOfMessages).isEqualTo("3");
    }

    private int getQueueTotalMessagesVisible(final LocalSqsAsyncClient client,
                                             final String queueUrl) throws InterruptedException, ExecutionException, TimeoutException {
        return client.getApproximateMessages(queueUrl).get(5, TimeUnit.SECONDS);
    }
}
