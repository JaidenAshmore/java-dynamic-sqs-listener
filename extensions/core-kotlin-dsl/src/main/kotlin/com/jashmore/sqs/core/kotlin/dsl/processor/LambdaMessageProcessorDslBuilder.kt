package com.jashmore.sqs.core.kotlin.dsl.processor

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.MessageProcessorDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.decorator.MessageProcessingDecorator
import com.jashmore.sqs.processor.LambdaMessageProcessor
import com.jashmore.sqs.processor.MessageProcessor
import com.jashmore.sqs.processor.argument.Acknowledge
import com.jashmore.sqs.processor.argument.VisibilityExtender
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message

@MessageListenerComponentDslMarker
class LambdaMessageProcessorDslBuilder(private val listenerIdentifier: String,
                                       private val sqsAsyncClient: SqsAsyncClient,
                                       private val queueProperties: QueueProperties) : MessageProcessorDslBuilder {

    /**
     * The list of [MessageProcessingDecorator]s that will wrap the processing of the message.
     */
    var decorators = listOf<MessageProcessingDecorator>()

    private var processorBuilder: () -> MessageProcessor = { throw RequiredFieldException("method", "LambdaMessageProcessor")}

    /**
     * Set the lambda as a method that just consumes the [Message].
     */
    fun method(func: (message: Message) -> Unit) {
        processorBuilder = {
            optionalDecoratedProcessor(
                    listenerIdentifier,
                    queueProperties,
                    decorators,
                    LambdaMessageProcessor(sqsAsyncClient, queueProperties, func)
            )
        }
    }

    /**
     * Set the lambda as a method that consumes the [Message] and has the responsibility of resolving the message
     * using the [Acknowledge] field.
     */
    fun method(func: (message: Message, acknowledge: Acknowledge) -> Unit) {
        processorBuilder = {
            optionalDecoratedProcessor(
                    listenerIdentifier,
                    queueProperties,
                    decorators,
                    LambdaMessageProcessor(sqsAsyncClient, queueProperties, func)
            )
        }
    }

    /**
     * Set the lambda as a method that consumes the [Message] and has the responsibility of resolving the message
     * using the [Acknowledge] field. It also has the [VisibilityExtender] that can be used to extend the length
     * of time that [Message] is not visible to other consumers.
     */
    fun method(func: (message: Message, acknowledge: Acknowledge, visibilityExtender: VisibilityExtender) -> Unit) {
        processorBuilder = {
            optionalDecoratedProcessor(
                    listenerIdentifier,
                    queueProperties,
                    decorators,
                    LambdaMessageProcessor(sqsAsyncClient, queueProperties, func)
            )
        }
    }

    /**
     * Set the lambda as a method that consumes the [Message] and has the [VisibilityExtender] that can be used to extend the length
     * of time that [Message] is not visible to other consumers. This is a different method name due to type erasure causing
     * the method signature to match others.
     */
    fun methodWithVisibilityExtender(func: (message: Message, visibilityExtender: VisibilityExtender) -> Unit) {
        processorBuilder = {
            optionalDecoratedProcessor(
                    listenerIdentifier,
                    queueProperties,
                    decorators,
                    LambdaMessageProcessor(sqsAsyncClient, queueProperties, true, func)
            )
        }
    }

    override fun invoke(): MessageProcessor = processorBuilder()
}

fun lambdaProcessor(identifier: String,
                    sqsAsyncClient: SqsAsyncClient,
                    queueProperties: QueueProperties,
                    init: LambdaMessageProcessorDslBuilder.() -> Unit): MessageProcessorDslBuilder {
    return initComponent(LambdaMessageProcessorDslBuilder(identifier, sqsAsyncClient, queueProperties), init)
}
