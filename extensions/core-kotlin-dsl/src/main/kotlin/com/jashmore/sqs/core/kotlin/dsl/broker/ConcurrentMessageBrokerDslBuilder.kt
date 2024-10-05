package com.jashmore.sqs.core.kotlin.dsl.broker

import com.jashmore.sqs.broker.MessageBroker
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.core.kotlin.dsl.MessageBrokerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import java.time.Duration

/**
 * [MessageBrokerDslBuilder] for building a [ConcurrentMessageBroker] for the [MessageListenerContainer].
 */
@MessageListenerComponentDslMarker
class ConcurrentMessageBrokerDslBuilder : MessageBrokerDslBuilder {
    /**
     * Supplier for getting the number of messages that should be processed concurrently.
     *
     * Each time a new message is begun to be processed this supplier will be checked and therefore it should be optimised via caching
     * or another method. This field may return different values each time it is checked and therefore the rate of concurrency can
     * be dynamically changed during runtime.
     *
     * @see [ConcurrentMessageBrokerProperties.getConcurrencyLevel] for in-depth details about this field
     */
    var concurrencyLevel: (() -> Int)? = null

    /**
     * Supplier for getting how long the broker should wait before checking to see if the concurrency rate can be increased if it is
     * already processing the concurrency level limit.
     *
     * @see [ConcurrentMessageBrokerProperties.getConcurrencyPollingRate] for in-depth details about this field
     */
    var concurrencyPollingRate: (() -> Duration?) = { null }

    /**
     * The amount of time that the broker should backoff if there was an error.
     *
     * This is used to stop it constantly cycling errors and spamming the logs and hopefully the error fixes itself the next time it is
     * attempted.
     *
     * @see [ConcurrentMessageBrokerProperties.getErrorBackoffTime] for in-depth details about this field
     */
    var errorBackoffTime: (() -> Duration?) = { null }

    override fun invoke(): MessageBroker {
        val actualConcurrencyLevel: () -> Int = concurrencyLevel ?: throw RequiredFieldException("concurrencyLevel", "ConcurrentMessageBroker")
        return ConcurrentMessageBroker(
            object : ConcurrentMessageBrokerProperties {

                override fun getConcurrencyLevel(): Int = actualConcurrencyLevel()

                override fun getConcurrencyPollingRate(): Duration? = concurrencyPollingRate()

                override fun getErrorBackoffTime(): Duration? = errorBackoffTime()
            }
        )
    }
}

/**
 * Create a [ConcurrentMessageBroker] using a Kotlin DSL format.
 *
 * Usage:
 * ```kotlin
 * val broker = concurrentBroker {
 *     // configure here
 * }
 * ```
 */
fun concurrentBroker(
    init: ConcurrentMessageBrokerDslBuilder.() -> Unit
) = initComponent(ConcurrentMessageBrokerDslBuilder(), init)
