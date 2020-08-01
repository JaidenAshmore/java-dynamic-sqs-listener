package com.jashmore.sqs.core.kotlin.dsl.processor

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.decorator.MessageProcessingDecorator
import com.jashmore.sqs.processor.DecoratingMessageProcessor
import com.jashmore.sqs.processor.MessageProcessor

/**
 * Wrap the delegate [MessageProcessor] in a [DecoratingMessageProcessor] if the list of decorators is not empty.
 */
fun optionalDecoratedProcessor(
    identifier: String,
    queueProperties: QueueProperties,
    decorators: List<MessageProcessingDecorator>,
    delegate: MessageProcessor
): MessageProcessor {
    if (decorators.isEmpty()) {
        return delegate
    }

    return DecoratingMessageProcessor(
        identifier,
        queueProperties,
        decorators,
        delegate
    )
}
