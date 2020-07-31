package com.jashmore.sqs.core.kotlin.dsl.container

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties
import com.jashmore.sqs.container.CoreMessageListenerContainer
import com.jashmore.sqs.container.CoreMessageListenerContainerProperties
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.retriever.PrefetchingMessageRetrieverDslBuilder
import com.jashmore.sqs.retriever.MessageRetriever
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

@MessageListenerComponentDslMarker
class PrefetchingMessageListenerContainerBuilder(identifier: String,
                                                 sqsAsyncClient: SqsAsyncClient,
                                                 queueProperties: QueueProperties) : MessageListenerContainerBuilder(identifier, sqsAsyncClient, queueProperties) {
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
     * The desired messages to be prefetched.
     *
     * @see PrefetchingMessageRetrieverProperties.getDesiredMinPrefetchedMessages for more details about this field
     */
    var desiredPrefetchedMessages: Int? = null
    /**
     * The maximum numbers that can be prefetched, must be less than [PrefetchingMessageRetrieverDslBuilder.desiredPrefetchedMessages].
     *
     * @see PrefetchingMessageRetrieverProperties.getMaxPrefetchedMessages for more details about this field
     */
    var maxPrefetchedMessages: Int? = null
    /**
     * Function for obtaining the visibility timeout for the message being retrieved.
     *
     * @see PrefetchingMessageRetrieverProperties.getMessageVisibilityTimeout for more details about this field
     */
    var messageVisibility: (() -> Duration?) = { Duration.ofSeconds(30) }
    /**
     * Set whether any extra messages that may have been internally stored in the [MessageRetriever] should be processed before shutting down.
     *
     * @see [CoreMessageListenerContainerProperties.shouldProcessAnyExtraRetrievedMessagesOnShutdown] for more details about this field
     */
    var processExtraMessagesOnShutdown: Boolean = true
    /**
     * Set whether the message processing threads should be interrupted when a shutdown is requested.
     *
     * @see [CoreMessageListenerContainerProperties.shouldInterruptThreadsProcessingMessagesOnShutdown] for more details about this field
     */
    var interruptThreadsProcessingMessagesOnShutdown: Boolean = false

    override fun invoke(): MessageListenerContainer {
        return coreMessageListener(identifier, sqsAsyncClient, queueProperties) {
            processor = this@PrefetchingMessageListenerContainerBuilder.processor
            broker = concurrentBroker {
                concurrencyLevel = this@PrefetchingMessageListenerContainerBuilder.concurrencyLevel
            }
            retriever = prefetchingMessageRetriever {
                desiredPrefetchedMessages = this@PrefetchingMessageListenerContainerBuilder.desiredPrefetchedMessages
                maxPrefetchedMessages = this@PrefetchingMessageListenerContainerBuilder.maxPrefetchedMessages
                messageVisibility = this@PrefetchingMessageListenerContainerBuilder.messageVisibility
            }
            resolver = batchingResolver()
            shutdown {
                shouldInterruptThreadsProcessingMessages = this@PrefetchingMessageListenerContainerBuilder.interruptThreadsProcessingMessagesOnShutdown
                shouldProcessAnyExtraRetrievedMessages = this@PrefetchingMessageListenerContainerBuilder.processExtraMessagesOnShutdown
            }
        }
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
 * val container = prefetchingMessageListener("identifier", sqsAsyncClient, "url") {
 *      concurrencyLevel = { 2 }
 *      desiredPrefetchedMessages = 5
 *      maxPrefetchedMessages = 10
 *
 *      // other configurations here...
 * }
 * ```
 *
 * @param identifier      the identifier that uniquely identifies this container
 * @param sqsAsyncClient  the client for communicating with the SQS server
 * @param queueUrl        the URL of the queue to listen to this
 * @param init            the function to configure this container
 * @return the message listener container
 */
fun prefetchingMessageListener(identifier: String,
                               sqsAsyncClient: SqsAsyncClient,
                               queueUrl: String,
                               init: PrefetchingMessageListenerContainerBuilder.() -> Unit): MessageListenerContainer {
    return prefetchingMessageListener(identifier, sqsAsyncClient, QueueProperties.builder().queueUrl(queueUrl).build(), init)
}

/**
 * Build a [CoreMessageListenerContainer] using a Kotlin DSL that will prefetch messages to process.
 *
 * This is equivalent to the @PrefetchingMessageListener annotation in the Spring implementation.
 *
 * Usage:
 *
 * ```kotlin
 * val container = prefetchingMessageListener("identifier", sqsAsyncClient, QueueProperties.builder().queueUrl("url").build()) {
 *      concurrencyLevel = { 2 }
 *      desiredPrefetchedMessages = 5
 *      maxPrefetchedMessages = 10
 *
 *      // other configurations here...
 * }
 * ```
 *
 * @param identifier      the identifier that uniquely identifies this container
 * @param sqsAsyncClient  the client for communicating with the SQS server
 * @param queueProperties details about the queue that is being listened to
 * @param init            the function to configure this container
 * @return the message listener container
 */
fun prefetchingMessageListener(identifier: String,
                               sqsAsyncClient: SqsAsyncClient,
                               queueProperties: QueueProperties,
                               init: PrefetchingMessageListenerContainerBuilder.() -> Unit): MessageListenerContainer {

    val listener = PrefetchingMessageListenerContainerBuilder(identifier, sqsAsyncClient, queueProperties)
    listener.init()
    return listener()
}