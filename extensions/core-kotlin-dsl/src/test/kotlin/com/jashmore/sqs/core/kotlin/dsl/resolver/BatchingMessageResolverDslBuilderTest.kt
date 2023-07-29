package com.jashmore.sqs.core.kotlin.dsl.resolver

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.resolver.MessageResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResultEntry
import software.amazon.awssdk.services.sqs.model.Message
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class BatchingMessageResolverDslBuilderTest {
    private val queueProperties = QueueProperties.builder()
        .queueUrl("url")
        .build()

    @Mock
    lateinit var sqsAsyncClient: SqsAsyncClient

    @Test
    fun `no batchSize will throw exception`() {
        val exception = Assertions.assertThrows(RequiredFieldException::class.java) {
            batchingResolver(sqsAsyncClient, queueProperties) {
                batchingPeriod = { Duration.ofSeconds(2) }
            }()
        }
        assertThat(exception).hasMessage("batchSize is required for BatchingMessageResolver")
    }

    @Test
    fun `no batchingPeriod will throw exception`() {
        val exception = Assertions.assertThrows(RequiredFieldException::class.java) {
            batchingResolver(sqsAsyncClient, queueProperties) {
                batchSize = { 1 }
            }()
        }
        assertThat(exception).hasMessage("batchingPeriod is required for BatchingMessageResolver")
    }

    @Test
    fun `buffering message resolver can be correctly built`() {
        // arrange
        var isFirstRun = true
        val batchingResolver = batchingResolver(sqsAsyncClient, queueProperties) {
            batchSize = {
                if (isFirstRun) {
                    isFirstRun = false
                    2
                } else {
                    throw InterruptedException()
                }
            }
            batchingPeriod = { Duration.ofMillis(500) }
        }()
        whenever(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest::class.java)))
            .thenReturn(
                CompletableFuture.completedFuture(
                    DeleteMessageBatchResponse.builder()
                        .successful(
                            listOf(
                                DeleteMessageBatchResultEntry.builder()
                                    .id("id")
                                    .build()
                            )
                        )
                        .failed(listOf<BatchResultErrorEntry>())
                        .build()
                )
            )

        // act
        val startTime = System.currentTimeMillis()
        batchingResolver.resolveMessage(Message.builder().messageId("id").receiptHandle("handle").body("body").build())
        runResolver(batchingResolver)
        val endTime = System.currentTimeMillis()

        // assert
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(500)
    }

    private fun runResolver(resolver: MessageResolver) = CompletableFuture.runAsync { resolver.run() }.get(5, TimeUnit.SECONDS)
}
