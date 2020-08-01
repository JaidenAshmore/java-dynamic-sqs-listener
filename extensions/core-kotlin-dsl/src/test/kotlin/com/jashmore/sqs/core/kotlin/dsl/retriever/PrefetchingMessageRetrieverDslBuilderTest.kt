package com.jashmore.sqs.core.kotlin.dsl.retriever

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.retriever.MessageRetriever
import com.jashmore.sqs.util.ExpectedTestException
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.stubbing.Answer
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.exception.SdkInterruptedException
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class PrefetchingMessageRetrieverDslBuilderTest {
    private val queueProperties = QueueProperties.builder()
        .queueUrl("url")
        .build()

    @Mock
    lateinit var sqsAsyncClient: SqsAsyncClient

    @Test
    fun `no desired prefetched messages set will throw exception`() {
        val exception = assertThrows(RequiredFieldException::class.java) {
            prefetchingMessageRetriever(sqsAsyncClient, queueProperties) {
                maxPrefetchedMessages = 2
            }()
        }

        assertThat(exception).hasMessage("desiredPrefetchedMessages is required for PrefetchingMessageRetriever")
    }

    @Test
    fun `no max prefetched messages set will throw exception`() {
        val exception = assertThrows(RequiredFieldException::class.java) {
            prefetchingMessageRetriever(sqsAsyncClient, queueProperties) {
                desiredPrefetchedMessages = 2
            }()
        }

        assertThat(exception).hasMessage("maxPrefetchedMessages is required for PrefetchingMessageRetriever")
    }

    @Test
    fun `setting visibility timeout using supplier will be included in request to SQS`() {
        // arrange
        whenever(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest::class.java)))
            .thenAnswer(runReceiveMessageOnce())
        var firstRequest = true

        // act
        val retriever = prefetchingMessageRetriever(sqsAsyncClient, queueProperties) {
            desiredPrefetchedMessages = 2
            maxPrefetchedMessages = 4
            messageVisibility = {
                if (firstRequest) {
                    firstRequest = false
                    Duration.ofSeconds(1)
                } else {
                    Duration.ofSeconds(2)
                }
            }
        }()
        runRetriever(retriever)

        // assert
        val sendMessageRequestCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest::class.java)
        verify(sqsAsyncClient, times(2)).receiveMessage(sendMessageRequestCaptor.capture())
        assertThat(sendMessageRequestCaptor.allValues[0].visibilityTimeout()).isEqualTo(Duration.ofSeconds(1).seconds)
        assertThat(sendMessageRequestCaptor.allValues[1].visibilityTimeout()).isEqualTo(Duration.ofSeconds(2).seconds)
    }

    @Test
    fun `setting error backoff time using supplier will wait that amount of time`() {
        // arrange
        whenever(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest::class.java)))
            .thenAnswer(runReceiveMessageFailure(times = 2))
        var isFirst = true

        // act
        val retriever = prefetchingMessageRetriever(sqsAsyncClient, queueProperties) {
            desiredPrefetchedMessages = 2
            maxPrefetchedMessages = 4
            errorBackoffTime = {
                if (isFirst) {
                    isFirst = false
                    Duration.ofMillis(500)
                } else {
                    Duration.ofMillis(1000)
                }
            }
        }()
        val startTime = System.currentTimeMillis()
        runRetriever(retriever)
        val endTime = System.currentTimeMillis()

        // assert
        assertThat(endTime - startTime).isGreaterThan(1500)
    }

    private fun runReceiveMessageOnce(): Answer<*> {
        var count = 0
        val times = 1
        return Answer {
            if (count < times) {
                ++count
                CompletableFuture.completedFuture(
                    ReceiveMessageResponse.builder()
                        .messages(listOf())
                        .build()
                )
            } else {
                throw SdkClientException.create("Expected Test Error", SdkInterruptedException())
            }
        }
    }

    private fun runReceiveMessageFailure(times: Int = 1): Answer<*> {
        var count = 0
        return Answer {
            if (count < times) {
                ++count
                throw ExpectedTestException()
            } else {
                throw SdkClientException.create("Expected Test Error", SdkInterruptedException())
            }
        }
    }

    private fun runRetriever(retriever: MessageRetriever) {
        CompletableFuture.runAsync { retriever.run() }.get(5, TimeUnit.SECONDS)
    }
}
