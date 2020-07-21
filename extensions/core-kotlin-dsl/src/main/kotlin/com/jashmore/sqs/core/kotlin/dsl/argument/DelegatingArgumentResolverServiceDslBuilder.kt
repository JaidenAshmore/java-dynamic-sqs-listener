package com.jashmore.sqs.core.kotlin.dsl.argument

import com.fasterxml.jackson.databind.ObjectMapper
import com.jashmore.sqs.core.kotlin.dsl.ArgumentResolverServiceDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.argument.ArgumentResolver
import com.jashmore.sqs.argument.ArgumentResolverService
import com.jashmore.sqs.argument.DelegatingArgumentResolverService
import com.jashmore.sqs.argument.attribute.MessageAttributeArgumentResolver
import com.jashmore.sqs.argument.attribute.MessageSystemAttributeArgumentResolver
import com.jashmore.sqs.argument.message.MessageArgumentResolver
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper
import com.jashmore.sqs.processor.MessageProcessor

/**
 * [ArgumentResolverServiceDslBuilder] that constructs a [DelegatingArgumentResolverService] for the [MessageProcessor].
 */
@MessageListenerComponentDslMarker
class DelegatingArgumentResolverServiceDslBuilder : ArgumentResolverServiceDslBuilder {
    private val argumentResolvers = mutableListOf<ArgumentResolver<*>>()

    /**
     * Add a [PayloadArgumentResolver] that is able to de-serialize message bodies using Jackson.
     *
     * @param objectMapper the optional object mapper to use, otherwise a default is used
     */
    fun jacksonPayloadResolver(objectMapper: ObjectMapper = ObjectMapper()) {
        add(PayloadArgumentResolver(JacksonPayloadMapper(objectMapper)))
    }

    /**
     * Add a [MessageIdArgumentResolver] that is used to resolve the message ID.
     */
    fun messageIdResolver() {
        add(MessageIdArgumentResolver())
    }

    /**
     * Add a [MessageArgumentResolver] that is used to resolve the original message.
     */
    fun messageResolver() {
        add(MessageArgumentResolver())
    }

    /**
     * Add a [MessageAttributeArgumentResolver] that is used to resolve a message attribute using Jackson serialization.
     *
     * @param objectMapper the optional object mapper to use, otherwise a default is used
     */
    fun messageAttributeResolver(objectMapper: ObjectMapper = ObjectMapper()) {
        add(MessageAttributeArgumentResolver(objectMapper))
    }

    /**
     * Add a [MessageSystemAttributeArgumentResolver] that is used to resolve a message system attribute.

     */
    fun messageSystemAttributeResolver() {
        add(MessageSystemAttributeArgumentResolver())
    }

    /**
     * Add a specific [ArgumentResolver].
     *
     * @param argumentResolver the resolver to add
     */
    fun add(argumentResolver: ArgumentResolver<*>) {
        argumentResolvers += argumentResolver
    }

    override fun invoke(): ArgumentResolverService {
        return DelegatingArgumentResolverService(
                argumentResolvers
        )
    }
}

/**
 * Use the [DelegatingArgumentResolverService] as the [ArgumentResolverService] for this processor.
 *
 * Usage:
 * ```kotlin
 * val argumentResolverService = delegatingArgumentResolverService {
 *     messageIdResolver()
 *     // other argument resolvers here...
 * }
 * ```
 *
 * @param init the DSL function for configuring this processor
 */
fun delegatingArgumentResolverService(init: DelegatingArgumentResolverServiceDslBuilder.() -> Unit)
        = initComponent(DelegatingArgumentResolverServiceDslBuilder(), init)
