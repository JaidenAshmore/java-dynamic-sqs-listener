package com.jashmore.sqs.ktor.container

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient
import com.jashmore.sqs.util.LocalSqsAsyncClient
import io.ktor.server.application.log
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val sqsClient: LocalSqsAsyncClient = ElasticMqSqsAsyncClient()

@AfterAll
fun tearDown() {
    sqsClient.close()
}

class KtorCoreExtensionTest {
    @Test
    fun `message listener can be registered`() {
        val queueUrl = sqsClient.createRandomQueue().get().queueUrl()
        val countDownLatch = CountDownLatch(1)
        testApplication {
            application {
                messageListener("core-listener", sqsClient, queueUrl) {
                    processor = lambdaProcessor {
                        method { message ->
                            log.info("Message: {}", message.body())
                            countDownLatch.countDown()
                        }
                    }
                    retriever = batchingMessageRetriever {
                        batchSize = { 1 }
                        batchingPeriod = { Duration.ofSeconds(30) }
                    }
                    resolver = batchingResolver {
                        batchSize = { 1 }
                        batchingPeriod = { Duration.ofSeconds(5) }
                    }
                    broker = concurrentBroker {
                        concurrencyLevel = { 5 }
                        concurrencyPollingRate = { Duration.ofMinutes(1) }
                    }
                }
            }
            startApplication()

            sqsClient.sendMessage { it.queueUrl(queueUrl).messageBody("body") }.get()

            assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun `prefetching message listener can be registered`() {
        val queueUrl = sqsClient.createRandomQueue().get().queueUrl()
        val countDownLatch = CountDownLatch(1)
        testApplication {
            application {
                prefetchingMessageListener("prefetching-listener", sqsClient, queueUrl) {
                    concurrencyLevel = { 5 }
                    desiredPrefetchedMessages = 1
                    maxPrefetchedMessages = 2
                    processor = lambdaProcessor {
                        method { message ->
                            log.info("Message: {}", message.body())
                            countDownLatch.countDown()
                        }
                    }
                }
            }
            startApplication()

            sqsClient.sendMessage { it.queueUrl(queueUrl).messageBody("body") }.get()

            assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun `batching message listener can be registered`() {
        val queueUrl = sqsClient.createRandomQueue().get().queueUrl()
        val countDownLatch = CountDownLatch(1)
        testApplication {
            application {
                batchingMessageListener("batching-listener", sqsClient, queueUrl) {
                    concurrencyLevel = { 5 }
                    processor = lambdaProcessor {
                        method { message ->
                            log.info("Message: {}", message.body())
                            countDownLatch.countDown()
                        }
                    }
                }
            }
            startApplication()

            sqsClient.sendMessage { it.queueUrl(queueUrl).messageBody("body") }.get()

            assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun `fifo message listener can be registered`() {
        val queueUrl = sqsClient.createRandomFifoQueue().get().response.queueUrl()
        val countDownLatch = CountDownLatch(1)
        testApplication {
            application {
                fifoMessageListener("fifo-listener", sqsClient, queueUrl) {
                    concurrencyLevel = { 5 }
                    processor = lambdaProcessor {
                        method { message ->
                            log.info("Message: {}", message.body())
                            countDownLatch.countDown()
                        }
                    }
                }
            }
            startApplication()

            sqsClient.sendMessage {
                it.queueUrl(queueUrl)
                    .messageBody("body")
                    .messageGroupId("groupId")
                    .messageDeduplicationId("dedupId")
            }.get()

            assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
        }
    }
}
