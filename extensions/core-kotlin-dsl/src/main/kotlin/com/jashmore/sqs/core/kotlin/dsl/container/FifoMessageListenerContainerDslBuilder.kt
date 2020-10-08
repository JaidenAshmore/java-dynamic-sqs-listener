package com.jashmore.sqs.core.kotlin.dsl.container

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.broker.grouping.GroupingMessageBrokerProperties
import com.jashmore.sqs.container.CoreMessageListenerContainer
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainer
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainerProperties
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

@MessageListenerComponentDslMarker
class FifoMessageListenerContainerDslBuilder(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueProperties: QueueProperties
) : AbstractMessageListenerContainerDslBuilder(identifier, sqsAsyncClient, queueProperties) {
    /**
     * Supplier for getting the number of messages that should be processed concurrently.
     *
     * Each time a new message is to be processed this supplier will be checked to see whether anymore concurrency is allowed. This function should therefore
     * be efficient and could be optimised by caching. This field may return different values each time it is checked and therefore the rate of concurrency can
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
     * The maximum number of messages that can be requested for a single message group at once.
     *
     * @see [BatchingMessageRetrieverProperties.getBatchSize] for more details about this field
     */
    var maximumMessagesRetrievedPerMessageGroup: (() -> Int) = { 1 }

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
     * Function for setting the visibility timeout for the message being retrieved.
     *
     * @see BatchingMessageRetrieverProperties.getMessageVisibilityTimeout for more details about this field
     */
    var messageVisibility: (() -> Duration?) = { null }

    override fun invoke(): MessageListenerContainer {
        val actualConcurrencyLevel = this.concurrencyLevel ?: throw RequiredFieldException("concurrencyLevel", "FifoMessageListener")
        return FifoMessageListenerContainer(
            identifier,
            queueProperties,
            sqsAsyncClient,
            this@FifoMessageListenerContainerDslBuilder.processor,
            object : FifoMessageListenerContainerProperties {

                override fun concurrencyLevel(): Int = actualConcurrencyLevel()

                override fun concurrencyPollingRate(): Duration? = this@FifoMessageListenerContainerDslBuilder.concurrencyPollingRate()

                override fun errorBackoffTime(): Duration? = this@FifoMessageListenerContainerDslBuilder.errorBackoffTime()

                override fun maximumMessagesInMessageGroup(): Int = this@FifoMessageListenerContainerDslBuilder.maximumMessagesRetrievedPerMessageGroup()

                override fun maximumCachedMessageGroups(): Int = this@FifoMessageListenerContainerDslBuilder.maximumNumberOfCachedMessageGroups()

                override fun messageVisibilityTimeout(): Duration? = this@FifoMessageListenerContainerDslBuilder.messageVisibility()
            }
        )
    }
}

/**
 * Build a [CoreMessageListenerContainer] using a Kotlin DSL that will prefetch messages to process.
 *
 * This is equivalent to the @PrefetchingQueueListener annotation in the Spring implementation.
 *
 * Usage:
 *
 * ```kotlin
 * val container = fifoMessageListener("identifier", sqsAsyncClient, "url") {
 *      concurrencyLevel = { 2 }
 *      desiredPrefetchedMessages = 5
 *      maxPrefetchedMessages = 10
 *
 *      // other configurations here...
 * }
 * ```
 *
 * @param identifier the identifier that uniquely identifies this container
 * @param sqsAsyncClient the client for communicating with the SQS server
 * @param queueUrl the URL of the queue to listen to this
 * @param init the function to configure this container
 * @return the message listener container
 */
fun fifoMessageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueUrl: String,
    init: FifoMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {
    return fifoMessageListener(identifier, sqsAsyncClient, QueueProperties.builder().queueUrl(queueUrl).build(), init)
}

/**
 * Build a [CoreMessageListenerContainer] using a Kotlin DSL that will prefetch messages to process.
 *
 * This is equivalent to the @PrefetchingMessageListener annotation in the Spring implementation.
 *
 * Usage:
 *
 * ```kotlin
 * val container = fifoMessageListener("identifier", sqsAsyncClient, QueueProperties.builder().queueUrl("url").build()) {
 *      concurrencyLevel = { 2 }
 *      desiredPrefetchedMessages = 5
 *      maxPrefetchedMessages = 10
 *
 *      // other configurations here...
 * }
 * ```
 *
 * @param identifier the identifier that uniquely identifies this container
 * @param sqsAsyncClient the client for communicating with the SQS server
 * @param queueProperties details about the queue that is being listened to
 * @param init the function to configure this container
 * @return the message listener container
 */
fun fifoMessageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueProperties: QueueProperties,
    init: FifoMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {

    val listener = FifoMessageListenerContainerDslBuilder(identifier, sqsAsyncClient, queueProperties)
    listener.init()
    return listener()
}
