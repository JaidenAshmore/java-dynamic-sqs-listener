package com.jashmore.sqs.core.kotlin.dsl.broker

import com.jashmore.sqs.broker.MessageBroker
import com.jashmore.sqs.broker.grouping.GroupingMessageBroker
import com.jashmore.sqs.broker.grouping.GroupingMessageBrokerProperties
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.core.kotlin.dsl.MessageBrokerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import software.amazon.awssdk.services.sqs.model.Message
import java.time.Duration
import java.util.function.Function

/**
 * [MessageBrokerDslBuilder] for building a [GroupingMessageBroker] for the [MessageListenerContainer].
 */
@MessageListenerComponentDslMarker
class GroupingMessageBrokerDslBuilder : MessageBrokerDslBuilder {
    /**
     * Supplier for getting the number of messages that should be processed concurrently.
     *
     * Each time a new message is begun to be processed this supplier will be checked and therefore it should be optimised via caching
     * or another method. This field may return different values each time it is checked and therefore the rate of concurrency can
     * be dynamically changed during runtime.
     *
     * @see [GroupingMessageBrokerProperties.getConcurrencyLevel] for more details about this field
     */
    var concurrencyLevel: (() -> Int)? = null

    /**
     * Supplier for getting how long the broker should wait before checking to see if the concurrency rate can be increased if it is
     * already processing the concurrency level limit.
     *
     * @see [GroupingMessageBrokerProperties.getConcurrencyPollingRate] for more details about this field
     */
    var concurrencyPollingRate: (() -> Duration?) = { null }

    /**
     * The amount of time that the broker should backoff if there was an error.
     *
     * This is used to stop it constantly cycling errors and spamming the logs and hopefully the error fixes itself the next time it is
     * attempted.
     *
     * @see [GroupingMessageBrokerProperties.getErrorBackoffTime] for more details about this field
     */
    var errorBackoffTime: (() -> Duration?) = { null }

    /**
     * The maximum number of message groups that can be cached before the {@link MessageBroker} should stop requesting more messages.
     *
     * This can be used to allow for the the {@link MessageBroker} to prefetch more message groups than can be concurrently processed to improve
     * performance. This value should be higher that {@link #getMaximumConcurrentMessageRetrieval()} as each message requested could be for a different
     * group.
     *
     * @see [GroupingMessageBrokerProperties.getMaximumNumberOfCachedMessageGroups] for more details about this field
     */
    var maximumNumberOfCachedMessageGroups: (() -> Int) = { 1 }

    /**
     * The function that can be used to group messages.
     *
     * @see [GroupingMessageBrokerProperties.messageGroupingFunction] for more details about this field
     */
    var messageGroupingFunction: ((message: Message) -> String)? = null

    override fun invoke(): MessageBroker {
        val concurrencyLevel = this.concurrencyLevel ?: throw RequiredFieldException("concurrencyLevel", "GroupingMessageBroker")
        val actualMessageGroupingFunction = this.messageGroupingFunction ?: throw RequiredFieldException("messageGroupingFunction", "GroupingMessageBroker")

        return GroupingMessageBroker(
            object : GroupingMessageBrokerProperties {
                override fun getConcurrencyLevel(): Int = concurrencyLevel()

                override fun getConcurrencyPollingRate(): Duration? = concurrencyPollingRate()

                override fun getErrorBackoffTime(): Duration? = errorBackoffTime()

                override fun getMaximumNumberOfCachedMessageGroups(): Int = maximumNumberOfCachedMessageGroups()

                override fun messageGroupingFunction(): Function<Message, String> = Function { t -> actualMessageGroupingFunction(t) }
            }
        )
    }
}

/**
 * Create a [GroupingMessageBroker] using a Kotlin DSL format.
 *
 * Usage:
 * ```kotlin
 * val broker = groupingBroker {
 *     // configure here
 * }
 * ```
 */
fun groupingBroker(init: GroupingMessageBrokerDslBuilder.() -> Unit) = initComponent(GroupingMessageBrokerDslBuilder(), init)
