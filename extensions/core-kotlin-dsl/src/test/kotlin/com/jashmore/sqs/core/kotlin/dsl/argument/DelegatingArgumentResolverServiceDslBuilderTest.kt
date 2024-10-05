package com.jashmore.sqs.core.kotlin.dsl.argument

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.argument.MethodParameter
import com.jashmore.sqs.argument.attribute.MessageAttribute
import com.jashmore.sqs.argument.attribute.MessageSystemAttribute
import com.jashmore.sqs.argument.attribute.MessageSystemAttributeArgumentResolver
import com.jashmore.sqs.argument.message.MessageArgumentResolver
import com.jashmore.sqs.argument.messageid.MessageId
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver
import com.jashmore.sqs.argument.payload.Payload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import java.lang.reflect.Method
import java.lang.reflect.Parameter

class DelegatingArgumentResolverServiceDslBuilderTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val queueProperties = QueueProperties.builder().queueUrl("url").build()
    private val method: Method = DelegatingArgumentResolverServiceDslBuilderTest::class.java.getMethod(
        "myMethod",
        User::class.java,
        String::class.java,
        User::class.java,
        String::class.java,
        Message::class.java
    )

    @Test
    fun `custom ArgumentResolver can be included`() {
        // arrange
        val messageIdArgumentResolver = MessageIdArgumentResolver()

        // act
        val argumentResolverServiceDslBuilder = delegatingArgumentResolverService {
            add(messageIdArgumentResolver)
        }
        val argumentResolverService = argumentResolverServiceDslBuilder()

        // assert
        assertThat(
            argumentResolverService.getArgumentResolver(MethodParameterRecord(method, parameterIndex = 1))
        ).isSameAs(messageIdArgumentResolver)
    }

    @Test
    fun `messageIdResolver can be be included`() {
        // act
        val argumentResolverServiceDslBuilder = delegatingArgumentResolverService {
            messageIdResolver()
        }
        val argumentResolverService = argumentResolverServiceDslBuilder()

        // assert
        assertThat(argumentResolverService.getArgumentResolver(MethodParameterRecord(method, parameterIndex = 1)))
            .isInstanceOf(MessageIdArgumentResolver::class.java)
    }

    @Test
    fun `messageResolver can be be included`() {
        // act
        val argumentResolverServiceDslBuilder = delegatingArgumentResolverService {
            messageResolver()
        }
        val argumentResolverService = argumentResolverServiceDslBuilder()

        // assert
        assertThat(argumentResolverService.getArgumentResolver(MethodParameterRecord(method, parameterIndex = 4)))
            .isInstanceOf(MessageArgumentResolver::class.java)
    }

    @Test
    fun `jacksonPayloadResolver can be included without a custom ObjectMapper`() {
        // arrange
        val message = Message.builder().body(objectMapper.writeValueAsString(User("name"))).build()
        val methodParameter = MethodParameterRecord(method, parameterIndex = 0)

        // act
        val argumentResolverServiceDslBuilder = delegatingArgumentResolverService {
            jacksonPayloadResolver()
        }
        val argumentResolverService = argumentResolverServiceDslBuilder()
        val argumentResolver = argumentResolverService.getArgumentResolver(methodParameter)
        val argument = argumentResolver.resolveArgumentForParameter(queueProperties, methodParameter, message)

        // assert
        assertThat(argument).isEqualTo(User("name"))
    }

    @Test
    fun `jacksonPayloadResolver can be included with a custom ObjectMapper`() {
        // arrange
        val message = Message.builder().body(objectMapper.writeValueAsString(User("name"))).build()
        val mockObjectMapper = mock(ObjectMapper::class.java)
        whenever(mockObjectMapper.readValue(anyString(), any(Class::class.java))).thenReturn(User("my-username"))

        // act
        val argumentResolverServiceDslBuilder = delegatingArgumentResolverService {
            jacksonPayloadResolver(mockObjectMapper)
        }
        val argumentResolverService = argumentResolverServiceDslBuilder()
        val methodParameter = MethodParameterRecord(method, parameterIndex = 0)
        val argumentResolver = argumentResolverService.getArgumentResolver(methodParameter)
        val argument = argumentResolver.resolveArgumentForParameter(queueProperties, methodParameter, message)

        // assert
        assertThat(argument).isEqualTo(User("my-username"))
    }

    @Test
    fun `messageAttributeResolver can be be included without a custom ObjectMapper`() {
        // arrange
        val message = Message.builder()
            .messageAttributes(
                mapOf(
                    "user" to MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(objectMapper.writeValueAsString(User("name")))
                        .build()
                )
            )
            .body("body")
            .build()
        val mockObjectMapper = mock(ObjectMapper::class.java)
        val methodParameter = MethodParameterRecord(method, parameterIndex = 2)
        whenever(mockObjectMapper.readValue(anyString(), any(Class::class.java))).thenReturn(User("my-username"))

        // act
        val argumentResolverServiceDslBuilder = delegatingArgumentResolverService {
            messageAttributeResolver(mockObjectMapper)
        }
        val argumentResolverService = argumentResolverServiceDslBuilder()
        val argumentResolver = argumentResolverService.getArgumentResolver(methodParameter)
        val argument = argumentResolver.resolveArgumentForParameter(queueProperties, methodParameter, message)

        // assert
        assertThat(argument).isEqualTo(User("my-username"))
    }

    @Test
    fun `messageAttributeResolver can be be included with a custom ObjectMapper`() {
        // arrange
        val message = Message.builder()
            .messageAttributes(
                mapOf(
                    "user" to MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(objectMapper.writeValueAsString(User("name")))
                        .build()
                )
            )
            .body("body")
            .build()
        val methodParameter = MethodParameterRecord(method, parameterIndex = 2)

        // act
        val argumentResolverServiceDslBuilder = delegatingArgumentResolverService {
            messageAttributeResolver()
        }
        val argumentResolverService = argumentResolverServiceDslBuilder()
        val argumentResolver = argumentResolverService.getArgumentResolver(methodParameter)
        val argument = argumentResolver.resolveArgumentForParameter(queueProperties, methodParameter, message)

        // assert
        assertThat(argument).isEqualTo(User("name"))
    }

    @Test
    fun `messageSystemAttributeResolver can be be included`() {
        // act
        val argumentResolverServiceDslBuilder = delegatingArgumentResolverService {
            messageSystemAttributeResolver()
        }
        val argumentResolverService = argumentResolverServiceDslBuilder()

        // assert
        assertThat(argumentResolverService.getArgumentResolver(MethodParameterRecord(method, parameterIndex = 3)))
            .isInstanceOf(MessageSystemAttributeArgumentResolver::class.java)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun myMethod(
        @Payload payload: User,
        @MessageId id: String,
        @MessageAttribute("user") userAttribute: User,
        @MessageSystemAttribute(MessageSystemAttributeName.AWS_TRACE_HEADER) systemAttribute: String,
        message: Message
    ) {
    }

    private class MethodParameterRecord(private val method: Method, private val parameterIndex: Int) : MethodParameter {
        override fun getParameter(): Parameter = method.parameters[parameterIndex]

        override fun getMethod(): Method = method

        override fun getParameterIndex(): Int = parameterIndex
    }

    @Suppress("unused")
    class User {
        var username: String?

        constructor() {
            this.username = null
        }

        constructor(username: String) {
            this.username = username
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as User

            if (username != other.username) return false

            return true
        }

        override fun hashCode(): Int {
            return username?.hashCode() ?: 0
        }
    }
}
