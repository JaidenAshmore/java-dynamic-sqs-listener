package com.jashmore.sqs.core.kotlin.dsl.decorators

import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.MessageProcessingDecoratorsDslBuilder
import com.jashmore.sqs.decorator.MessageProcessingDecorator

/**
 * Core [MessageProcessingDecoratorsDslBuilder] that just simply adds all the decorators to a list.
 */
@MessageListenerComponentDslMarker
class CoreMessageProcessingDecoratorsDslDslBuilder : MessageProcessingDecoratorsDslBuilder {
    private val decorators = mutableListOf<MessageProcessingDecorator>()

    override fun add(decorator: MessageProcessingDecorator) {
        decorators.add(decorator)
    }

    override fun invoke(): List<MessageProcessingDecorator> {
        return decorators.toList()
    }
}
