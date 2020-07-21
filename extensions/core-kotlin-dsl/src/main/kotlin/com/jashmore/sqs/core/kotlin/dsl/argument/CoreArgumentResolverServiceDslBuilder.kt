package com.jashmore.sqs.core.kotlin.dsl.argument

import com.fasterxml.jackson.databind.ObjectMapper
import com.jashmore.sqs.argument.ArgumentResolverService
import com.jashmore.sqs.argument.CoreArgumentResolverService
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper
import com.jashmore.sqs.core.kotlin.dsl.ArgumentResolverServiceDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.processor.MessageProcessor

/**
 * [ArgumentResolverServiceDslBuilder] that constructs a [CoreArgumentResolverService] for the [MessageProcessor].
 */
@MessageListenerComponentDslMarker
class CoreArgumentResolverServiceDslBuilder(private val objectMapper: ObjectMapper) : ArgumentResolverServiceDslBuilder {

    override fun invoke(): ArgumentResolverService {
        return CoreArgumentResolverService(
                JacksonPayloadMapper(objectMapper),
                objectMapper
        )
    }
}

/**
 * Use the [CoreArgumentResolverService] as the [ArgumentResolverService] for the processor.
 *
 * Usage:
 * ```kotlin
 * val argumentResolverServiceBuilder = coreArgumentResolverService(ObjectMapper())
 * ```
 *
 * @param init the DSL function for configuring this processor
 */
fun coreArgumentResolverService(objectMapper: ObjectMapper = ObjectMapper(), init: CoreArgumentResolverServiceDslBuilder.() -> Unit = { })
        = initComponent(CoreArgumentResolverServiceDslBuilder(objectMapper), init)