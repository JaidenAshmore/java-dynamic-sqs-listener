package com.jashmore.sqs.core.kotlin.dsl

import com.jashmore.sqs.argument.ArgumentResolverService
import com.jashmore.sqs.broker.MessageBroker
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.processor.MessageProcessor
import com.jashmore.sqs.resolver.MessageResolver
import com.jashmore.sqs.retriever.MessageRetriever

@DslMarker
annotation class MessageListenerComponentDslMarker

/**
 * Used to build a single component of the [MessageListenerContainer].
 *
 * @param <T> the type of the component being built
 */
typealias MessageListenerComponentDslBuilder<T> = () -> T

typealias MessageRetrieverDslBuilder = MessageListenerComponentDslBuilder<MessageRetriever>

typealias MessageProcessorDslBuilder = MessageListenerComponentDslBuilder<MessageProcessor>

typealias ArgumentResolverServiceDslBuilder = MessageListenerComponentDslBuilder<ArgumentResolverService>

typealias MessageBrokerDslBuilder = MessageListenerComponentDslBuilder<MessageBroker>

typealias MessageResolverDslBuilder = MessageListenerComponentDslBuilder<MessageResolver>

fun <T> initComponent(componentBuilder: T, init: T.() -> Unit): T {
    componentBuilder.init()
    return componentBuilder
}
