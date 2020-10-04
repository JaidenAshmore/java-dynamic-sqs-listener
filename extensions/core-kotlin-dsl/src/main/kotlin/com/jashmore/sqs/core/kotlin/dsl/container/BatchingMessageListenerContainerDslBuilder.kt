package com.jashmore.sqs.core.kotlin.dsl.container

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties
import com.jashmore.sqs.container.CoreMessageListenerContainer
import com.jashmore.sqs.container.CoreMessageListenerContainerProperties
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainer
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainerProperties
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.retriever.BatchingMessageRetrieverDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.resolver.batching.BatchingMessageResolverProperties
import com.jashmore.sqs.retriever.MessageRetriever
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

@MessageListenerComponentDslMarker
class BatchingMessageListenerContainerDslBuilder(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueProperties: QueueProperties
) : AbstractMessageListenerContainerDslBuilder(identifier, sqsAsyncClient, queueProperties) {
    companion object {
        private const val DEFAULT_BATCH_SIZE = 5
        private val DEFAULT_BATCHING_PERIOD = Duration.ofSeconds(2)
        private val DEFAULT_MESSAGE_VISIBILITY = Duration.ofSeconds(30)
    }

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
     * @see DEFAULT_BATCH_SIZE for the default batch size
     */
    var batchSize: (() -> Int) = { DEFAULT_BATCH_SIZE }
    /**
     * The maximum amount of time to wait for the number of messages requested to reach the [BatchingMessageRetrieverDslBuilder.batchSize].
     *
     * @see BatchingMessageRetrieverProperties.getBatchingPeriod for more details about this field
     * @see BatchingMessageResolverProperties.getBufferingTime for more details about this field
     * @see DEFAULT_BATCHING_PERIOD for the default batching period
     */
    var batchingPeriod: (() -> Duration) = { DEFAULT_BATCHING_PERIOD }
    /**
     * Function for obtaining the visibility timeout for the message being retrieved.
     *
     * @see PrefetchingMessageRetrieverProperties.getMessageVisibilityTimeout for more details about this field
     * @see DEFAULT_MESSAGE_VISIBILITY for the default message visibility
     */
    var messageVisibility: (() -> Duration?) = { DEFAULT_MESSAGE_VISIBILITY }
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
        val actualConcurrencyLevel = concurrencyLevel ?: throw RequiredFieldException("concurrencyLevel", "BatchingMessageListenerContainer")
        return BatchingMessageListenerContainer(
            identifier,
            queueProperties,
            sqsAsyncClient,
            this@BatchingMessageListenerContainerDslBuilder.processor,
            object : BatchingMessageListenerContainerProperties {
                override fun concurrencyLevel(): Int = actualConcurrencyLevel()

                override fun concurrencyPollingRate(): Duration? = null

                override fun errorBackoffTime(): Duration? = null

                override fun batchSize(): Int = this@BatchingMessageListenerContainerDslBuilder.batchSize()

                override fun getBatchingPeriod(): Duration = batchingPeriod()

                override fun messageVisibilityTimeout(): Duration? = messageVisibility()

                override fun processAnyExtraRetrievedMessagesOnShutdown(): Boolean =
                    this@BatchingMessageListenerContainerDslBuilder.processExtraMessagesOnShutdown

                override fun interruptThreadsProcessingMessagesOnShutdown(): Boolean =
                    this@BatchingMessageListenerContainerDslBuilder.interruptThreadsProcessingMessagesOnShutdown
            }
        )
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
 * @param identifier the identifier that uniquely identifies this container
 * @param sqsAsyncClient the client for communicating with the SQS server
 * @param queueUrl the URL of the queue to listen to this
 * @param init the function to configure this container
 * @return the message listener container
 */
fun batchingMessageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueUrl: String,
    init: BatchingMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {
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
 * @param identifier the identifier that uniquely identifies this container
 * @param sqsAsyncClient the client for communicating with the SQS server
 * @param queueProperties details about the queue that is being listened to
 * @param init the function to configure this container
 * @return the message listener container
 */
fun batchingMessageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueProperties: QueueProperties,
    init: BatchingMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {

    val listener = BatchingMessageListenerContainerDslBuilder(identifier, sqsAsyncClient, queueProperties)
    listener.init()
    return listener()
}
