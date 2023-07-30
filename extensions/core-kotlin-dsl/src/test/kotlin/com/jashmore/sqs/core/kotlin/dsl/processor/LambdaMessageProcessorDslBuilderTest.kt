package com.jashmore.sqs.core.kotlin.dsl.processor

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.processor.argument.Acknowledge
import com.jashmore.sqs.processor.argument.VisibilityExtender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
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
class LambdaMessageProcessorDslBuilderTest {
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
        val processor = lambdaProcessor("identifier", sqsAsyncClient, queueProperties) {
            method { message -> messageReference.set(message) }
        }()

        // act
        processor.processMessage(message, resolveMessage)

        // assert
        assertThat(messageReference).hasValue(message)
    }

    @Test
    fun `can provide a lambda with acknowledge that must be used to resolve a message`() {
        // arrange
        var acknowledge: Acknowledge? = null
        val processor = lambdaProcessor("identifier", sqsAsyncClient, queueProperties) {
            method { _, ack -> acknowledge = ack }
        }()

        // act
        val future = processor.processMessage(message, resolveMessage)
        assertThat(future).isCompleted()
        verify(resolveMessage, never()).get()
        acknowledge?.acknowledgeSuccessful()

        // assert
        verify(resolveMessage).get()
    }

    @Test
    fun `can provide a lambda with acknowledge and visibility extender`() {
        // arrange
        val processor = lambdaProcessor("identifier", sqsAsyncClient, queueProperties) {
            method { _, acknowledge, visibilityExtender ->
                acknowledge.acknowledgeSuccessful()
                visibilityExtender.extend()
            }
        }()

        // act
        val future = processor.processMessage(message, resolveMessage)

        // assert
        assertThat(future).isCompleted()
        verify(sqsAsyncClient).changeMessageVisibility(
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
        val processor = lambdaProcessor("identifier", sqsAsyncClient, queueProperties) {
            methodWithVisibilityExtender { _, _ -> }
        }()

        // act
        val future = processor.processMessage(message, resolveMessage)

        // assert
        assertThat(future).isCompleted()
        verify(resolveMessage).get()
    }
}
