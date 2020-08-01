package com.jashmore.sqs.core.kotlin.dsl.container

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageProcessorDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.processor.AsyncLambdaMessageProcessorDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.processor.CoreMessageProcessorDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.processor.LambdaMessageProcessorDslBuilder
import com.jashmore.sqs.processor.AsyncLambdaMessageProcessor
import com.jashmore.sqs.processor.CoreMessageProcessor
import com.jashmore.sqs.processor.LambdaMessageProcessor
import com.jashmore.sqs.processor.MessageProcessor
import software.amazon.awssdk.services.sqs.SqsAsyncClient

/**
 * Abstract class for building a [MessageListenerContainer] that allows the implementer to use their own [MessageListenerContainer] implementation.
 *
 * <p>This only provides access to the processor object
 */
abstract class AbstractMessageListenerContainerDslBuilder(val identifier: String, val sqsAsyncClient: SqsAsyncClient, val queueProperties: QueueProperties) :
    MessageListenerComponentDslBuilder<MessageListenerContainer> {
    var processor: MessageProcessorDslBuilder? = null

    /**
     * Use the [CoreMessageProcessor] as the [MessageProcessor] in this container.
     *
     * Usage:
     * ```kotlin
     * val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
     *     processor = coreProcessor {
     *        // configure here
     *     }
     *     // other configuration
     * }
     * ```
     *
     * @param init the DSL function for configuring this processor
     */
    fun coreProcessor(init: CoreMessageProcessorDslBuilder.() -> Unit) =
        com.jashmore.sqs.core.kotlin.dsl.processor.coreProcessor(identifier, sqsAsyncClient, queueProperties, init)

    /**
     * Use the [LambdaMessageProcessor] as the [MessageProcessor] in this container.
     *
     * Usage:
     * ```kotlin
     * val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
     *     processor = lambdaProcessor { message ->
     *        // do message processing here
     *     }
     *     // other configuration
     * }
     * ```
     */
    fun lambdaProcessor(init: LambdaMessageProcessorDslBuilder.() -> Unit) =
        com.jashmore.sqs.core.kotlin.dsl.processor.lambdaProcessor(identifier, sqsAsyncClient, queueProperties, init)

    /**
     * Use the [AsyncLambdaMessageProcessor] as the [MessageProcessor] in this container.
     *
     * Usage:
     * ```kotlin
     * val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
     *     processor = asyncLambdaProcessor { message ->
     *        // do message processing here
     *
     *        return CompletableFuture.completedFuture(null);
     *     }
     *     // other configuration
     * }
     * ```
     */
    fun asyncLambdaProcessor(init: AsyncLambdaMessageProcessorDslBuilder.() -> Unit) =
        com.jashmore.sqs.core.kotlin.dsl.processor.asyncLambdaProcessor(identifier, sqsAsyncClient, queueProperties, init)
}
