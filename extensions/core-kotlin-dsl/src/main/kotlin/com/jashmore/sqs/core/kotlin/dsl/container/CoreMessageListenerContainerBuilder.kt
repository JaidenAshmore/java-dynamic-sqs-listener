package com.jashmore.sqs.core.kotlin.dsl.container

import com.jashmore.sqs.core.kotlin.dsl.MessageBrokerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerContainerBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageProcessorDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageResolverDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.MessageRetrieverDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.broker.MessageBroker
import com.jashmore.sqs.container.CoreMessageListenerContainer
import com.jashmore.sqs.container.CoreMessageListenerContainerProperties
import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.resolver.MessageResolver
import com.jashmore.sqs.retriever.MessageRetriever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration
import java.util.function.Supplier

/**
 * [MessageListenerContainerBuilder] that will construct a [MessageListenerContainer] for usage in this container.
 */
@MessageListenerComponentDslMarker
class CoreMessageListenerContainerBuilder(identifier: String, sqsAsyncClient: SqsAsyncClient, queueProperties: QueueProperties)
    : MessageListenerContainerBuilder(identifier, sqsAsyncClient, queueProperties) {
    private var shutdownBuilder: ShutdownBuilder? = null

    /**
     * Configure the shutdown properties for this container.
     */
    fun shutdown(init: ShutdownBuilder.() -> Unit) {
        shutdownBuilder = initComponent(ShutdownBuilder(), init)
    }

    override fun invoke(): MessageListenerContainer {
        val brokerBuilder: MessageBrokerDslBuilder = broker ?: throw RequiredFieldException("broker", "CoreMessageListenerContainer")
        val retrieverBuilder: MessageRetrieverDslBuilder = retriever ?: throw RequiredFieldException("retriever", "CoreMessageListenerContainer")
        val processorBuilder: MessageProcessorDslBuilder = processor ?: throw RequiredFieldException("processor", "CoreMessageListenerContainer")
        val resolverBuilder: MessageResolverDslBuilder = resolver ?: throw RequiredFieldException("resolver", "CoreMessageListenerContainer")
        val shutdown = shutdownBuilder
        if (shutdown != null) {
            return CoreMessageListenerContainer(identifier,
                    Supplier(brokerBuilder),
                    Supplier(retrieverBuilder),
                    Supplier(processorBuilder),
                    Supplier(resolverBuilder),
                    object : CoreMessageListenerContainerProperties {
                        override fun getMessageRetrieverShutdownTimeoutInSeconds(): Int? = shutdown.messageRetrieverShutdownTimeout?.seconds?.toInt()

                        override fun shouldInterruptThreadsProcessingMessagesOnShutdown(): Boolean? = shutdown.shouldInterruptThreadsProcessingMessages

                        override fun getMessageResolverShutdownTimeoutInSeconds(): Int? = shutdown.messageResolverShutdownTimeout?.seconds?.toInt()

                        override fun shouldProcessAnyExtraRetrievedMessagesOnShutdown(): Boolean? = shutdown.shouldProcessAnyExtraRetrievedMessages

                        override fun getMessageProcessingShutdownTimeoutInSeconds(): Int? = shutdown.messageProcessingShutdownTimeout?.seconds?.toInt()
                    }
            )
        } else {
            return CoreMessageListenerContainer(identifier,
                    Supplier(brokerBuilder),
                    Supplier(retrieverBuilder),
                    Supplier(processorBuilder),
                    Supplier(resolverBuilder)
            )
        }
    }
}

@MessageListenerComponentDslMarker
class ShutdownBuilder {
    /**
     * Set whether any extra messages that may have been internally stored in the [MessageRetriever] should be processed before shutting down.
     *
     * @see [CoreMessageListenerContainerProperties.shouldProcessAnyExtraRetrievedMessagesOnShutdown] for more details about this field
     */
    var shouldProcessAnyExtraRetrievedMessages: Boolean? = null
    /**
     * Set whether the message processing threads should be interrupted when a shutdown is requested.
     *
     * @see [CoreMessageListenerContainerProperties.shouldInterruptThreadsProcessingMessagesOnShutdown] for more details about this field
     */
    var shouldInterruptThreadsProcessingMessages: Boolean? = null

    /**
     * Set the timeout for how long to wait for the message processing threads to finish.
     *
     * @see [CoreMessageListenerContainerProperties.getMessageProcessingShutdownTimeoutInSeconds] for more details about this field
     */
    var messageProcessingShutdownTimeout: Duration? = null
    /**
     * Set the timeout for how long to wait for the [MessageRetriever] background thread to finish.
     *
     * @see [CoreMessageListenerContainerProperties.getMessageRetrieverShutdownTimeoutInSeconds] for more details about this field
     */
    var messageRetrieverShutdownTimeout: Duration? = null
    /**
     * Set the timeout for how to wait for the [MessageResolver] background thread to finish.
     *
     * @see [CoreMessageListenerContainerProperties.getMessageResolverShutdownTimeoutInSeconds] for more details about this field
     */
    var messageResolverShutdownTimeout: Duration? = null
    /**
     * Set the timeout for how to wait for the [MessageBroker] background thread to finish.
     *
     * Note that if the [ShutdownBuilder.shouldProcessAnyExtraRetrievedMessages] is true and there are extra messages to process, this timeout will
     * be used twice.
     *
     * @see [CoreMessageListenerContainerProperties.getMessageBrokerShutdownTimeoutInSeconds] for more details about this field
     */
    var messageBrokerShutdownTimeout: Duration? = null
}

/**
 * Build a [CoreMessageListenerContainer] using a Kotlin DSL.
 *
 * Usage:
 *
 * ```kotlin
 * val container = coreMessageListener("identifier", sqsAsyncClient, "someUrl") {
 *      processor = coreMessageProcessor {
 *           // configure this processor...
 *      }
 *
 *      retriever = prefetchingMessageRetriever {
 *          // configure this retriever...
 *      }
 *
 *      // other configurations here...
 * }
 * ```
 *
 * @param identifier     the identifier that uniquely identifies this container
 * @param sqsAsyncClient the client for communicating with the SQS server
 * @param queueUrl       the URL of the SQS queue to listen to
 * @param init           the function to configure this container
 */
fun coreMessageListener(identifier: String,
                        sqsAsyncClient: SqsAsyncClient,
                        queueUrl: String,
                        init: CoreMessageListenerContainerBuilder.() -> Unit): MessageListenerContainer {
    return coreMessageListener(identifier, sqsAsyncClient, QueueProperties.builder().queueUrl(queueUrl).build(), init)
}

/**
 * Build a [CoreMessageListenerContainer] using a Kotlin DSL.
 *
 * Usage:
 *
 * ```kotlin
 * val container = coreMessageListener("identifier", sqsAsyncClient, QueueProperties.builder().queueUrl("url").build()) {
 *      processor = coreMessageProcessor {
 *           // configure this processor...
 *      }
 *
 *      retriever = prefetchingMessageRetriever {
 *          // configure this retriever...
 *      }
 *
 *      // other configurations here...
 * }
 * ```
 *
 * @param identifier      the identifier that uniquely identifies this container
 * @param sqsAsyncClient  the client for communicating with the SQS server
 * @param queueProperties details about the queue that is being listened to
 * @param init            the function to configure this container
 */
fun coreMessageListener(identifier: String,
                        sqsAsyncClient: SqsAsyncClient,
                        queueProperties: QueueProperties,
                        init: CoreMessageListenerContainerBuilder.() -> Unit): MessageListenerContainer {

    val listener = CoreMessageListenerContainerBuilder(identifier, sqsAsyncClient, queueProperties)
    listener.init()
    return listener.invoke()
}
