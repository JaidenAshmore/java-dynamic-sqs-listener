package com.jashmore.sqs.core.kotlin.dsl.container

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient
import com.jashmore.sqs.util.ExpectedTestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random

class FifoMessageListenerContainerDslBuilderTest {
    lateinit var container: MessageListenerContainer

    @AfterEach
    fun tearDown() {
        container.stop()
    }

    @Test
    fun `minimal configuration`() {
        // arrange
        val sqsAsyncClient = ElasticMqSqsAsyncClient()
        val queueUrl = sqsAsyncClient.createRandomFifoQueue().get().queueUrl()
        val countDownLatch = CountDownLatch(1)

        // act
        container = fifoMessageListener("identifier", sqsAsyncClient, queueUrl) {
            concurrencyLevel = { 1 }
            processor = lambdaProcessor {
                method { _ -> countDownLatch.countDown() }
            }
        }
        container.start()
        sqsAsyncClient.sendMessage {
            it.queueUrl(
                queueUrl
            ).messageGroupId("groupId").messageBody("body").messageDeduplicationId("id")
        }
        // assert
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `message processing with errors`() {
        // arrange
        val sqsAsyncClient = ElasticMqSqsAsyncClient()
        val queueProperties = createFifoQueueWithDlq(sqsAsyncClient)
        val failedMessageIds = ConcurrentHashMap.newKeySet<String>()
        val numberOfMessageGroups = 10
        val numberOfMessages = 5
        val successfulMessageLatch = CountDownLatch(numberOfMessageGroups * numberOfMessages)
        val processedMessages = (0 until numberOfMessageGroups)
            .map { "$it" }
            .associateWith { mutableListOf<String>() }
        sendMessages(sqsAsyncClient, queueProperties, numberOfMessageGroups, numberOfMessages)
        val lock = ReentrantLock()

        // act
        container = fifoMessageListener("identifier", sqsAsyncClient, queueProperties.queueUrl) {
            concurrencyLevel = { 10 }
            maximumNumberOfCachedMessageGroups = { 4 }
            maximumMessagesRetrievedPerMessageGroup = { 2 }
            processor = asyncLambdaProcessor {
                method { message ->
                    CompletableFuture.runAsync {
                        try {
                            Thread.sleep(100)
                        } catch (interruptedException: InterruptedException) {
                            return@runAsync
                        }
                        lock.lock()
                        try {
                            val groupKey: String = message.attributes()[MessageSystemAttributeName.MESSAGE_GROUP_ID]!!
                            if (!failedMessageIds.contains(groupKey + "-" + message.body()) && Random.nextInt(10) < 3) {
                                failedMessageIds.add(groupKey + "-" + message.body())
                                throw ExpectedTestException()
                            }
                            processedMessages[groupKey]?.add(message.body())
                            successfulMessageLatch.countDown()
                        } finally {
                            lock.unlock()
                        }
                    }
                }
            }
        }
        container.start()
        successfulMessageLatch.await(2, TimeUnit.MINUTES)
        container.stop()

        // assert
        assertThat(processedMessages).containsOnlyKeys((0 until numberOfMessageGroups).map { "$it" })
        assertThat(processedMessages)
            .allSatisfy { _, messagesNumbers ->
                assertThat(
                    messagesNumbers
                ).containsExactlyElementsOf((0 until numberOfMessages).map { "$it" })
            }
    }

    private fun sendMessages(
        sqsAsyncClient: ElasticMqSqsAsyncClient,
        queueProperties: QueueProperties,
        numberOfGroups: Int,
        numberOfMessages: Int
    ) {
        for (i in 0 until numberOfMessages) {
            sqsAsyncClient
                .sendMessageBatch { sendMessageBuilder ->
                    val entries = (0 until numberOfGroups)
                        .map {
                            val messageId = "$i-$it"
                            SendMessageBatchRequestEntry
                                .builder()
                                .id(messageId)
                                .messageGroupId("$it")
                                .messageBody("$i")
                                .messageDeduplicationId(messageId)
                                .build()
                        }
                    sendMessageBuilder.queueUrl(queueProperties.queueUrl).entries(entries)
                }
                .get(5, TimeUnit.SECONDS)
        }
    }

    private fun createFifoQueueWithDlq(sqsAsyncClient: ElasticMqSqsAsyncClient): QueueProperties {
        val deadLetterQueueResponse = sqsAsyncClient.createRandomFifoQueue().get()
        val attributes = sqsAsyncClient
            .getQueueAttributes { builder: GetQueueAttributesRequest.Builder ->
                builder.queueUrl(
                    deadLetterQueueResponse.queueUrl()
                ).attributeNames(QueueAttributeName.QUEUE_ARN)
            }
            .get()
        val queueUrl = sqsAsyncClient
            .createRandomFifoQueue { builder ->
                builder.attributes(
                    mapOf(
                        QueueAttributeName.REDRIVE_POLICY to """{
                            |  "deadLetterTargetArn": "${attributes.attributes()[QueueAttributeName.QUEUE_ARN]}",
                            |  "maxReceiveCount":"3"
                            |}
                        """.trimMargin(),
                        QueueAttributeName.VISIBILITY_TIMEOUT to "10"
                    )
                )
            }
            .get()
            .queueUrl()
        return QueueProperties.builder().queueUrl(queueUrl).build()
    }
}
