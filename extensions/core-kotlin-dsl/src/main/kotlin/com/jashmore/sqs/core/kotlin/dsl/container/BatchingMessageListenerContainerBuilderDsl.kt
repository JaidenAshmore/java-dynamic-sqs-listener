package com.jashmore.sqs.core.kotlin.dsl.container

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties
import com.jashmore.sqs.container.CoreMessageListenerContainer
import com.jashmore.sqs.container.CoreMessageListenerContainerProperties
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.retriever.BatchingMessageRetrieverDslBuilder
import com.jashmore.sqs.resolver.batching.BatchingMessageResolverProperties
import com.jashmore.sqs.retriever.MessageRetriever
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

@MessageListenerComponentDslMarker
class BatchingMessageListenerContainerBuilder(identifier: String,
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
     * The batch size for the number of messages to receive at once
     *
     * @see BatchingMessageRetrieverProperties.getBatchSize for more details about this field
     * @see BatchingMessageResolverProperties.getBufferingSizeLimit for more details about this field
     */
    var batchSize: (() -> Int) = { 5 }
    /**
     * The maximum amount of time to wait for the number of messages requested to reach the [BatchingMessageRetrieverDslBuilder.batchSize].
     *
     * @see BatchingMessageRetrieverProperties.getBatchingPeriod for more details about this field
     * @see BatchingMessageResolverProperties.getBufferingTime for more details about this field
     */
    var batchingPeriod: (() -> Duration) = { Duration.ofSeconds(2) }
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
            processor = this@BatchingMessageListenerContainerBuilder.processor
            broker = concurrentBroker {
                concurrencyLevel = this@BatchingMessageListenerContainerBuilder.concurrencyLevel
            }
            retriever = batchingMessageRetriever {
                batchSize = this@BatchingMessageListenerContainerBuilder.batchSize
                batchingPeriod = this@BatchingMessageListenerContainerBuilder.batchingPeriod
                messageVisibility = this@BatchingMessageListenerContainerBuilder.messageVisibility
            }
            resolver = batchingResolver {
                batchSize = this@BatchingMessageListenerContainerBuilder.batchSize
                batchingPeriod = this@BatchingMessageListenerContainerBuilder.batchingPeriod
            }
            shutdown {
                shouldInterruptThreadsProcessingMessages = this@BatchingMessageListenerContainerBuilder.interruptThreadsProcessingMessagesOnShutdown
                shouldProcessAnyExtraRetrievedMessages = this@BatchingMessageListenerContainerBuilder.processExtraMessagesOnShutdown
            }
        }
    }
}

/**
 * Build a [CoreMessageListenerContainer] using a Kotlin DSL that will batch requests to retrieve messages to process.
 *
 * This is equivalent to the @QueueListener annotation in the Spring implementation.
 *
 * Usage:
 *
 * ```kotlin
 * val container = batchingMessageListener("identifier", sqsAsyncClient, "url") {
 *      concurrencyLevel = { 2 }
 *      batchSize = { 5 }
 *      batchingPeriod = { Duration.ofSeconds(2) }
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
fun batchingMessageListener(identifier: String,
                            sqsAsyncClient: SqsAsyncClient,
                            queueUrl: String,
                            init: BatchingMessageListenerContainerBuilder.() -> Unit): MessageListenerContainer {
    return batchingMessageListener(identifier, sqsAsyncClient, QueueProperties.builder().queueUrl(queueUrl).build(), init)
}

/**
 * Build a [CoreMessageListenerContainer] using a Kotlin DSL that will batch requests to retrieve messages to process.
 *
 * This is equivalent to the @QueueListener annotation in the Spring implementation.
 *
 * Usage:
 *
 * ```kotlin
 * val container = batchingMessageListener("identifier", sqsAsyncClient, QueueProperties.builder().queueUrl("url").build()) {
 *      concurrencyLevel = { 2 }
 *      batchSize = { 5 }
 *      batchingPeriod = { Duration.ofSeconds(2) }
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
fun batchingMessageListener(identifier: String,
                            sqsAsyncClient: SqsAsyncClient,
                            queueProperties: QueueProperties,
                            init: BatchingMessageListenerContainerBuilder.() -> Unit): MessageListenerContainer {

    val listener = BatchingMessageListenerContainerBuilder(identifier, sqsAsyncClient, queueProperties)
    listener.init()
    return listener()
}
