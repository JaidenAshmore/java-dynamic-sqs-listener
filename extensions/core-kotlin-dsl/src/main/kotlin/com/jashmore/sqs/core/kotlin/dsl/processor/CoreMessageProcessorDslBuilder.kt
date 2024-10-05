package com.jashmore.sqs.core.kotlin.dsl.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.argument.ArgumentResolverService
import com.jashmore.sqs.argument.CoreArgumentResolverService
import com.jashmore.sqs.core.kotlin.dsl.ArgumentResolverServiceDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.MessageProcessorDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.argument.CoreArgumentResolverServiceDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.decorator.MessageProcessingDecorator
import com.jashmore.sqs.processor.CoreMessageProcessor
import com.jashmore.sqs.processor.MessageProcessor
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.lang.reflect.Method

/**
 * [MessageProcessorDslBuilder] that will construct a [CoreMessageProcessor] for usage in this container.
 */
@MessageListenerComponentDslMarker
class CoreMessageProcessorDslBuilder(
    private val listenerIdentifier: String,
    private val sqsAsyncClient: SqsAsyncClient,
    private val queueProperties: QueueProperties
) : MessageProcessorDslBuilder {
    /**
     * The builder for instantiating the [ArgumentResolverService] to be used.
     *
     * If this is not supplied, a default [CoreArgumentResolverService] will be used which configures the core argument resolvers.
     */
    var argumentResolverService: ArgumentResolverServiceDslBuilder = CoreArgumentResolverServiceDslBuilder(
        ObjectMapper()
    )

    /**
     * The object instance that will execute the message listener method.
     */
    var bean: Any? = null

    /**
     * The method that will invoke the message listener logic.
     */
    var method: Method? = null

    /**
     * The list of [MessageProcessingDecorator]s that will wrap the processing of the message.
     */
    var decorators = listOf<MessageProcessingDecorator>()

    override fun invoke(): MessageProcessor {
        return optionalDecoratedProcessor(
            listenerIdentifier,
            queueProperties,
            decorators,
            CoreMessageProcessor(
                argumentResolverService.invoke(),
                queueProperties,
                sqsAsyncClient,
                method ?: throw RequiredFieldException("method", "CoreMessageProcessor"),
                bean ?: throw RequiredFieldException("bean", "CoreMessageProcessor")

            )
        )
    }
}

fun coreProcessor(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueProperties: QueueProperties,
    init: CoreMessageProcessorDslBuilder.() -> Unit
) =
    initComponent(CoreMessageProcessorDslBuilder(identifier, sqsAsyncClient, queueProperties), init)
