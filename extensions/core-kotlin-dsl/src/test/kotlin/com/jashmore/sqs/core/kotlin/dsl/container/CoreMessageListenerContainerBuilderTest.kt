package com.jashmore.sqs.core.kotlin.dsl.container

import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CoreMessageListenerContainerBuilderTest {

    var container: MessageListenerContainer? = null

    @AfterEach
    internal fun tearDown() {
        container?.stop()
    }

    @Test
    fun `minimal configuration`() {
        // arrange
        val sqsAsyncClient = ElasticMqSqsAsyncClient()
        val queueUrl = sqsAsyncClient.createRandomQueue().get().queueUrl()
        val countDownLatch = CountDownLatch(1)

        // act
        container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
            processor = coreProcessor {
                bean = MessageListener(countDownLatch)
                method = MessageListener::class.java.getMethod("processMessage")
            }
            retriever = prefetchingMessageRetriever {
                desiredPrefetchedMessages = 1
                maxPrefetchedMessages = 2
            }
            resolver = batchingResolver {
                bufferingSizeLimit = { 1 }
                bufferingTime = { Duration.ofSeconds(1) }
            }
            broker = concurrentBroker {
                concurrencyLevel = { 1 }
            }
        }
        container?.start()
        sqsAsyncClient.sendMessage { it.queueUrl(queueUrl).messageBody("body") }

        // assert
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
    }


    @Test
    fun `can configure batching message retriever`() {
        // arrange
        val sqsAsyncClient = ElasticMqSqsAsyncClient()
        val queueUrl = sqsAsyncClient.createRandomQueue().get().queueUrl()
        val countDownLatch = CountDownLatch(1)

        // act
        container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
            processor = coreProcessor {
                bean = MessageListener(countDownLatch)
                method = MessageListener::class.java.getMethod("processMessage")
            }
            retriever = batchingMessageRetriever {
                batchSize = { 1 }
                batchingPeriod = { Duration.ofSeconds(5) }
            }
            resolver = batchingResolver {
                bufferingSizeLimit = { 1 }
                bufferingTime = { Duration.ofSeconds(1) }
            }
            broker = concurrentBroker {
                concurrencyLevel = { 1 }
            }
        }
        container?.start()
        sqsAsyncClient.sendMessage { it.queueUrl(queueUrl).messageBody("body") }

        // assert
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `can configure shutdown configuration for container`() {
        // arrange
        val sqsAsyncClient = ElasticMqSqsAsyncClient()
        val queueUrl = sqsAsyncClient.createRandomQueue().get().queueUrl()
        val countDownLatch = CountDownLatch(1)

        // act
         container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
            processor = coreProcessor {
                bean = MessageListener(countDownLatch)
                method = MessageListener::class.java.getMethod("processMessage")
            }
            retriever = prefetchingMessageRetriever {
                desiredPrefetchedMessages = 1
                maxPrefetchedMessages = 2
            }
            resolver = batchingResolver {
                bufferingSizeLimit = { 1 }
                bufferingTime = { Duration.ofSeconds(1) }
            }
            broker = concurrentBroker {
                concurrencyLevel = { 1 }
            }
            shutdown {
                messageBrokerShutdownTimeout = Duration.ofSeconds(5)
                messageProcessingShutdownTimeout = Duration.ofSeconds(5)
                messageResolverShutdownTimeout = Duration.ofSeconds(5)
                messageRetrieverShutdownTimeout = Duration.ofSeconds(5)
                shouldInterruptThreadsProcessingMessages = true
                shouldProcessAnyExtraRetrievedMessages = true
            }
        }
        container?.start()
        container?.stop()
        sqsAsyncClient.close()
    }

    inner class MessageListener(private val countDownLatch: CountDownLatch) {
        @Suppress("unused")
        fun processMessage() {
            countDownLatch.countDown()
        }
    }
}