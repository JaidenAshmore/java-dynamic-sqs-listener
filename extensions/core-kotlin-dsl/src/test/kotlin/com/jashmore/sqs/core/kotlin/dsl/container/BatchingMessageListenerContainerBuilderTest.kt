package com.jashmore.sqs.core.kotlin.dsl.container

import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BatchingMessageListenerContainerBuilderTest {

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
        container = batchingMessageListener("identifier", sqsAsyncClient, queueUrl) {
            concurrencyLevel = { 1 }
            batchSize = { 5 }
            batchingPeriod = { Duration.ofSeconds(1) }
            processor = lambdaProcessor {
                method { _ ->
                    countDownLatch.countDown()
                }
            }
        }
        container?.start()
        sqsAsyncClient.sendMessage { it.queueUrl(queueUrl).messageBody("body") }

        // assert
        Assertions.assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
    }
}