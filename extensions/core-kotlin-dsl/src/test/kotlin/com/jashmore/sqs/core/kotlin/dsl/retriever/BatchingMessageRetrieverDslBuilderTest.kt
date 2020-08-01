package com.jashmore.sqs.core.kotlin.dsl.retriever

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.retriever.MessageRetriever
import com.jashmore.sqs.util.ExpectedTestException
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.exception.SdkInterruptedException
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@ExtendWith(MockitoExtension::class)
class BatchingMessageRetrieverDslBuilderTest {
    private val queueProperties = QueueProperties.builder()
        .queueUrl("url")
        .build()

    @Mock
    lateinit var sqsAsyncClient: SqsAsyncClient

    @Test
    fun `no batchSize set will throw exception`() {
        val exception = Assertions.assertThrows(RequiredFieldException::class.java) {
            batchingMessageRetriever(sqsAsyncClient, queueProperties) {
                batchingPeriod = { Duration.ofSeconds(5) }
            }()
        }

        assertThat(exception).hasMessage("batchSize is required for BatchingMessageRetriever")
    }

    @Test
    fun `no batchingPeriod set will throw exception`() {
        val exception = Assertions.assertThrows(RequiredFieldException::class.java) {
            batchingMessageRetriever(sqsAsyncClient, queueProperties) {
                batchSize = { 2 }
            }()
        }

        assertThat(exception).hasMessage("batchingPeriod is required for BatchingMessageRetriever")
    }

    @Test
    fun `will wait supplied batching period before requesting messages`() {
        // arrange
        var count = 0
        whenever(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest::class.java)))
            .thenAnswer {
                ++count
                when (count) {
                    1 ->
                        CompletableFuture.completedFuture(
                            ReceiveMessageResponse.builder()
                                .messages(listOf())
                                .build()
                        )
                    else -> throw SdkClientException.create("Expected Test Error", SdkInterruptedException())
                }
            }

        // act
        val retriever = batchingMessageRetriever(sqsAsyncClient, queueProperties) {
            batchSize = { 2 }
            batchingPeriod = { Duration.ofMillis(200) }
        }()
        val startTime = System.currentTimeMillis()
        retriever.retrieveMessage()
        runRetriever(retriever)
        val endTime = System.currentTimeMillis()

        // assert
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(200)
    }

    @Test
    fun `will request messages as soon as the batch size is reached`() {
        // arrange
        var count = 0
        whenever(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest::class.java)))
            .thenAnswer {
                ++count
                when (count) {
                    1 ->
                        CompletableFuture.completedFuture(
                            ReceiveMessageResponse.builder()
                                .messages(listOf())
                                .build()
                        )
                    else -> throw SdkClientException.create("Expected Test Error", SdkInterruptedException())
                }
            }

        // act
        val retriever = batchingMessageRetriever(sqsAsyncClient, queueProperties) {
            batchSize = { 2 }
            batchingPeriod = { Duration.ofMillis(200) }
        }()
        val startTime = System.currentTimeMillis()
        retriever.retrieveMessage()
        retriever.retrieveMessage()
        runRetriever(retriever)
        val endTime = System.currentTimeMillis()

        // assert
        assertThat(endTime - startTime).isLessThan(200)
    }

    @Test
    fun `setting error backoff time using supplier will wait that amount of time`() {
        // arrange
        var count = 0
        whenever(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest::class.java)))
            .thenAnswer {
                ++count
                when (count) {
                    1 -> throw ExpectedTestException()
                    else -> throw SdkClientException.create("Expected Test Error", SdkInterruptedException())
                }
            }

        // act
        val retriever = batchingMessageRetriever(sqsAsyncClient, queueProperties) {
            batchSize = { 1 }
            batchingPeriod = { Duration.ofSeconds(5) }
            errorBackoffTime = { Duration.ofMillis(500) }
        }()
        val startTime = System.currentTimeMillis()
        retriever.retrieveMessage()
        runRetriever(retriever)
        val endTime = System.currentTimeMillis()

        // assert
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(500)
    }

    @Test
    fun `setting message visibility using supplier will set that in request`() {
        // arrange
        val actualRequest = AtomicReference<ReceiveMessageRequest>()
        whenever(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest::class.java)))
            .thenAnswer {
                actualRequest.set(it.arguments[0] as ReceiveMessageRequest)
                throw SdkClientException.create("Expected Test Error", SdkInterruptedException())
            }

        // act
        val retriever = batchingMessageRetriever(sqsAsyncClient, queueProperties) {
            batchSize = { 1 }
            batchingPeriod = { Duration.ofSeconds(5) }
            messageVisibility = { Duration.ofSeconds(30) }
        }()
        retriever.retrieveMessage()
        runRetriever(retriever)

        // assert
        assertThat(actualRequest.get().visibilityTimeout()).isEqualTo(30)
    }

    private fun runRetriever(retriever: MessageRetriever) {
        CompletableFuture.runAsync { retriever.run() }.get(5, TimeUnit.SECONDS)
    }
}
