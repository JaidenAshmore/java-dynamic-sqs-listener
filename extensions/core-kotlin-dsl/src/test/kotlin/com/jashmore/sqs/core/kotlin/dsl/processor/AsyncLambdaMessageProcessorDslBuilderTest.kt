package com.jashmore.sqs.core.kotlin.dsl.processor

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.processor.argument.Acknowledge
import com.jashmore.sqs.processor.argument.VisibilityExtender
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest
import software.amazon.awssdk.services.sqs.model.Message
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

private val queueProperties = QueueProperties.builder()
    .queueUrl("url")
    .build()
private val message = Message.builder().receiptHandle("handle").build()

@ExtendWith(MockitoExtension::class)
class AsyncLambdaMessageProcessorDslBuilderTest {
    @Mock
    lateinit var sqsAsyncClient: SqsAsyncClient

    @Mock
    lateinit var resolveMessage: Supplier<CompletableFuture<*>>

    @BeforeEach
    fun setUp() {
        whenever(resolveMessage.get()).thenReturn(CompletableFuture.completedFuture(null))
    }

    @Test
    fun `can provide a lambda with only a message field`() {
        // arrange
        val messageReference = AtomicReference<Message>()
        val processor = asyncLambdaProcessor("identifier", sqsAsyncClient, queueProperties) {
            method { message ->
                messageReference.set(message)
                CompletableFuture.completedFuture(null)
            }
        }()

        // act
        processor.processMessage(message, resolveMessage)

        // assert
        Assertions.assertThat(messageReference).hasValue(message)
    }

    @Test
    fun `can provide a lambda with acknowledge that must be used to resolve a message`() {
        // arrange
        var acknowledge: Acknowledge? = null
        val processor = asyncLambdaProcessor("identifier", sqsAsyncClient, queueProperties) {
            method { _, ack ->
                acknowledge = ack
                CompletableFuture.completedFuture(null)
            }
        }()

        // act
        val future = processor.processMessage(message, resolveMessage)
        Assertions.assertThat(future).isCompleted()
        Mockito.verify(resolveMessage, Mockito.never()).get()
        acknowledge?.acknowledgeSuccessful()

        // assert
        Mockito.verify(resolveMessage).get()
    }

    @Test
    fun `can provide a lambda with acknowledge and visibility extender`() {
        // arrange
        val processor = asyncLambdaProcessor("identifier", sqsAsyncClient, queueProperties) {
            method { _, acknowledge, visibilityExtender ->
                visibilityExtender.extend()
                acknowledge.acknowledgeSuccessful()
                CompletableFuture.completedFuture(null)
            }
        }()

        // act
        val future = processor.processMessage(message, resolveMessage)

        // assert
        Assertions.assertThat(future).isCompleted()
        Mockito.verify(sqsAsyncClient).changeMessageVisibility(
            ChangeMessageVisibilityRequest.builder()
                .visibilityTimeout(VisibilityExtender.DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS)
                .queueUrl("url")
                .receiptHandle("handle")
                .build()
        )
    }

    @Test
    fun `can provide a lambda with a message and visibility extender`() {
        // arrange
        val processor = asyncLambdaProcessor("identifier", sqsAsyncClient, queueProperties) {
            methodWithVisibilityExtender { _, _ -> CompletableFuture.completedFuture(null) }
        }()

        // act
        val future = processor.processMessage(message, resolveMessage)

        // assert
        Assertions.assertThat(future).isCompleted()
        Mockito.verify(resolveMessage).get()
    }
}
