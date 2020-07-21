package com.jashmore.sqs.core.kotlin.dsl

import com.jashmore.sqs.core.kotlin.dsl.broker.ConcurrentMessageBrokerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.processor.CoreMessageProcessorDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.resolver.BatchingMessageResolverDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.retriever.PrefetchingMessageRetrieverDslBuilder
import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.argument.ArgumentResolverService
import com.jashmore.sqs.broker.MessageBroker
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.core.kotlin.dsl.retriever.BatchingMessageRetrieverDslBuilder
import com.jashmore.sqs.decorator.MessageProcessingDecorator
import com.jashmore.sqs.processor.CoreMessageProcessor
import com.jashmore.sqs.processor.MessageProcessor
import com.jashmore.sqs.resolver.MessageResolver
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver
import com.jashmore.sqs.retriever.MessageRetriever
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever
import software.amazon.awssdk.services.sqs.SqsAsyncClient

@DslMarker
annotation class MessageListenerComponentDslMarker

/**
 * Used to build a single component of the [MessageListenerContainer].
 *
 * @param <T> the type of the component being built
 */
typealias MessageListenerComponentDslBuilder<T> = () -> T

interface MessageRetrieverDslBuilder : MessageListenerComponentDslBuilder<MessageRetriever>

interface MessageProcessorDslBuilder : MessageListenerComponentDslBuilder<MessageProcessor>

interface ArgumentResolverServiceDslBuilder : MessageListenerComponentDslBuilder<ArgumentResolverService>

interface MessageBrokerDslBuilder : MessageListenerComponentDslBuilder<MessageBroker>

interface MessageResolverDslBuilder : MessageListenerComponentDslBuilder<MessageResolver>

interface MessageProcessingDecoratorsDslBuilder: MessageListenerComponentDslBuilder<List<MessageProcessingDecorator>> {
    /**
     * Add a decorator to the chain of [MessageProcessingDecorator]s.
     *
     * @param decorator the decorator to add
     */
    fun add(decorator: MessageProcessingDecorator)
}

/**
 * Abstract class for building a [MessageListenerContainer] that allows the implementer to use their own [MessageListenerContainer] implementation.
 */
@MessageListenerComponentDslMarker
abstract class MessageListenerContainerBuilder(val identifier: String, val sqsAsyncClient: SqsAsyncClient, val queueProperties: QueueProperties) : MessageListenerComponentDslBuilder<MessageListenerContainer> {
    var retriever: MessageRetrieverDslBuilder? = null
    var processor: MessageProcessorDslBuilder? = null
    var broker: MessageBrokerDslBuilder? = null
    var resolver: MessageResolverDslBuilder? = null

    /**
     * Use the [ConcurrentMessageBroker] as the [MessageBroker] in this container.
     *
     * Usage:
     * ```kotlin
     * val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
     *     broker = concurrentBroker {
     *        // configure here
     *     }
     *     // other configuration
     * }
     * ```
     */
    fun concurrentBroker(init: ConcurrentMessageBrokerDslBuilder.() -> Unit) = com.jashmore.sqs.core.kotlin.dsl.broker.concurrentBroker(init)

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
    fun coreProcessor(init: CoreMessageProcessorDslBuilder.() -> Unit)
            = com.jashmore.sqs.core.kotlin.dsl.processor.coreProcessor(identifier, sqsAsyncClient, queueProperties, init)

    /**
     * Use the [BatchingMessageRetriever] as the [MessageRetriever] for this container.
     *
     * Usage:
     * ```kotlin
     * val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
     *     retriever = batchingMessageRetriever {
     *        // configure here
     *     }
     *     // other configuration
     * }
     * ```
     *
     * @param init the DSL function for configuring this resolver
     */
    fun batchingMessageRetriever(init: BatchingMessageRetrieverDslBuilder.() -> Unit)
            = com.jashmore.sqs.core.kotlin.dsl.retriever.batchingMessageRetriever(sqsAsyncClient, queueProperties, init)

    /**
     * Use the [PrefetchingMessageRetriever] as the [MessageRetriever] for this container.
     *
     * Usage:
     * ```kotlin
     * val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
     *     retriever = prefetchingMessageRetriever {
     *        // configure here
     *     }
     *     // other configuration
     * }
     * ```
     *
     * @param init the DSL function for configuring this resolver
     */
    fun prefetchingMessageRetriever(init: PrefetchingMessageRetrieverDslBuilder.() -> Unit)
            = com.jashmore.sqs.core.kotlin.dsl.retriever.prefetchingMessageRetriever(sqsAsyncClient, queueProperties, init)

    /**
     * Use the [BatchingMessageResolver] as the [MessageResolver] for this container.
     *
     * Usage:
     * ```kotlin
     * val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
     *     resolver = batchingResolver {
     *        // configure here
     *     }
     *     // other configuration
     * }
     * ```
     *
     * @param init the DSL function for configuring this resolver
     */
    fun batchingResolver(init: BatchingMessageResolverDslBuilder.() -> Unit)
            = com.jashmore.sqs.core.kotlin.dsl.resolver.batchingResolver(sqsAsyncClient, queueProperties, init)
}

fun <T> initComponent(componentBuilder: T, init: T.() -> Unit): T {
    componentBuilder.init()
    return componentBuilder
}
