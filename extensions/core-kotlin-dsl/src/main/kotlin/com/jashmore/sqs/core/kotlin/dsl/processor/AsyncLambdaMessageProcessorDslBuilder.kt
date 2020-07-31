package com.jashmore.sqs.core.kotlin.dsl.processor

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.MessageProcessorDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.decorator.MessageProcessingDecorator
import com.jashmore.sqs.processor.AsyncLambdaMessageProcessor
import com.jashmore.sqs.processor.MessageProcessor
import com.jashmore.sqs.processor.argument.Acknowledge
import com.jashmore.sqs.processor.argument.VisibilityExtender
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import java.util.concurrent.CompletableFuture

@MessageListenerComponentDslMarker
class AsyncLambdaMessageProcessorDslBuilder(private val listenerIdentifier: String,
                                            private val sqsAsyncClient: SqsAsyncClient,
                                            private val queueProperties: QueueProperties) : MessageProcessorDslBuilder {

    var decorators = mutableListOf<MessageProcessingDecorator>()

    private var processorBuilder: () -> MessageProcessor = { throw RequiredFieldException("method", "LambdaMessageProcessor") }

    fun method(func: (message: Message) -> CompletableFuture<*>) {
        processorBuilder = {
            optionalDecoratedProcessor(
                    listenerIdentifier,
                    queueProperties,
                    decorators,
                    AsyncLambdaMessageProcessor(sqsAsyncClient, queueProperties, func)
            )
        }
    }

    fun method(func: (message: Message, acknowledge: Acknowledge) -> CompletableFuture<*>) {
        processorBuilder = {
            optionalDecoratedProcessor(
                    listenerIdentifier,
                    queueProperties,
                    decorators,
                    AsyncLambdaMessageProcessor(sqsAsyncClient, queueProperties, func)
            )
        }
    }

    fun method(func: (message: Message, acknowledge: Acknowledge, visibilityExtender: VisibilityExtender) -> CompletableFuture<*>) {
        processorBuilder = {
            optionalDecoratedProcessor(
                    listenerIdentifier,
                    queueProperties,
                    decorators,
                    AsyncLambdaMessageProcessor(sqsAsyncClient, queueProperties, func)
            )
        }
    }

    fun methodWithVisibilityExtender(func: (message: Message, visibilityExtender: VisibilityExtender) -> CompletableFuture<*>) {
        processorBuilder = {
            optionalDecoratedProcessor(
                    listenerIdentifier,
                    queueProperties,
                    decorators,
                    AsyncLambdaMessageProcessor(sqsAsyncClient, queueProperties, true, func)
            )
        }
    }

    override fun invoke(): MessageProcessor = processorBuilder()
}



fun asyncLambdaProcessor(identifier: String,
                         sqsAsyncClient: SqsAsyncClient,
                         queueProperties: QueueProperties,
                         init: AsyncLambdaMessageProcessorDslBuilder.() -> Unit): MessageProcessorDslBuilder {
    return initComponent(AsyncLambdaMessageProcessorDslBuilder(identifier, sqsAsyncClient, queueProperties), init)
}