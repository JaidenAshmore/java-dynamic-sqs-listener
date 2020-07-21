package com.jashmore.sqs.core.kotlin.dsl.processor

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.argument.ArgumentResolver
import com.jashmore.sqs.argument.MethodParameter
import com.jashmore.sqs.argument.payload.Payload
import com.jashmore.sqs.core.kotlin.dsl.argument.delegatingArgumentResolverService
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.decorator.MessageProcessingDecorator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val payloadReference: AtomicReference<String> = AtomicReference()

@ExtendWith(MockitoExtension::class)
class CoreMessageProcessorDslBuilderTest {
    private val queueProperties = QueueProperties.builder()
            .queueUrl("url")
            .build()

    @Mock
    lateinit var sqsAsyncClient: SqsAsyncClient

    @BeforeEach
    internal fun setUp() {
        payloadReference.set(null)
    }

    @Test
    fun `no bean will throw exception building component`() {
        // act
        val exception = assertThrows(RequiredFieldException::class.java) {
            coreProcessor("identifier", sqsAsyncClient, queueProperties) {
                method = CoreMessageProcessorDslBuilderTest::class.java.getMethod("myMethod", String::class.java)
            }()
        }

        // assert
        assertThat(exception).hasMessage("bean is required for CoreMessageProcessor")
    }

    @Test
    fun `no method will throw exception building component`() {
        // act
        val exception = assertThrows(RequiredFieldException::class.java) {
            coreProcessor("identifier", sqsAsyncClient, queueProperties) {
                bean = Object()
            }()
        }

        // assert
        assertThat(exception).hasMessage("method is required for CoreMessageProcessor")
    }

    @Test
    fun `no argument resolver will set up defaults`() {
        // act
        val coreProcessor = coreProcessor("identifier", sqsAsyncClient, queueProperties) {
            bean = CoreMessageProcessorDslBuilderTest()
            method = CoreMessageProcessorDslBuilderTest::class.java.getMethod("myMethod", String::class.java)
        }()

        coreProcessor.processMessage(Message.builder().body("test").build()) { CompletableFuture.completedFuture(null) }.get(5, TimeUnit.SECONDS)

        // assert
        assertThat(payloadReference.get()).isEqualTo("test")
    }

    @Test
    fun `custom argument resolvers can be configured`() {
        // act
        val coreProcessor = coreProcessor("identifier", sqsAsyncClient, queueProperties) {
            argumentResolverService = delegatingArgumentResolverService {
                add(object : ArgumentResolver<String> {
                    override fun canResolveParameter(methodParameter: MethodParameter?): Boolean = true

                    override fun resolveArgumentForParameter(queueProperties: QueueProperties,
                                                             methodParameter: MethodParameter,
                                                             message: Message): String = "some value"
                })
            }
            bean = CoreMessageProcessorDslBuilderTest()
            method = CoreMessageProcessorDslBuilderTest::class.java.getMethod("myMethod", String::class.java)
        }()

        coreProcessor.processMessage(Message.builder().body("test").build()) { CompletableFuture.completedFuture(null) }.get(5, TimeUnit.SECONDS)

        // assert
        assertThat(payloadReference.get()).isEqualTo("some value")
    }

    @Test
    fun `adding decorators will wrap in DecoratingMessageProcessor`() {
        // act
        val mockDecorator = mock(MessageProcessingDecorator::class.java)
        val coreProcessor = coreProcessor("identifier", sqsAsyncClient, queueProperties) {
            argumentResolverService = delegatingArgumentResolverService {
                add(object : ArgumentResolver<String> {
                    override fun canResolveParameter(methodParameter: MethodParameter?): Boolean = true

                    override fun resolveArgumentForParameter(queueProperties: QueueProperties,
                                                             methodParameter: MethodParameter,
                                                             message: Message): String = "some value"
                })
            }
            bean = CoreMessageProcessorDslBuilderTest()
            method = CoreMessageProcessorDslBuilderTest::class.java.getMethod("myMethod", String::class.java)
            decorators {
                add(mockDecorator)
            }
        }()

        val message = Message.builder().body("test").build()
        coreProcessor.processMessage(message) { CompletableFuture.completedFuture(null) }.get(5, TimeUnit.SECONDS)

        // assert
        verify(mockDecorator).onPreMessageProcessing(any(), eq(message))
    }

    @Suppress("unused")
    fun myMethod(@Payload payload: String) {
        payloadReference.set(payload)
    }
}
