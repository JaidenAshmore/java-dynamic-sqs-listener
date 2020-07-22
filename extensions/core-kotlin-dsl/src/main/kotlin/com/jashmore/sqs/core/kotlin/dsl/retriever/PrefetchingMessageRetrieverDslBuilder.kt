package com.jashmore.sqs.core.kotlin.dsl.retriever

import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.MessageRetrieverDslBuilder
import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.retriever.MessageRetriever
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

/**
 * The [MessageRetrieverDslBuilder] hat will construct a [PrefetchingMessageRetriever] for usage in this container.
 */
@MessageListenerComponentDslMarker
class PrefetchingMessageRetrieverDslBuilder(private val sqsAsyncClient: SqsAsyncClient,
                                            private val queueProperties: QueueProperties) : MessageRetrieverDslBuilder {
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
    var messageVisibility: (() -> Duration?)? = null

    /**
     * Function for obtaining the error backoff time if there is an error retrieving messages.
     *
     * This helps stop the application constantly throwing errors if there was a problem.
     */
    var errorBackoffTime: (() -> Duration?)? = null

    override fun invoke(): MessageRetriever {
        val actualDesiredPrefetchedMessages: Int = desiredPrefetchedMessages ?: throw RequiredFieldException("desiredPrefetchedMessages", "PrefetchingMessageRetriever")
        val actualMaxPrefetched: Int = maxPrefetchedMessages ?: throw RequiredFieldException("maxPrefetchedMessages", "PrefetchingMessageRetriever")
        val actualMessageVisibility: () -> Duration? = messageVisibility ?: { null }
        val actualErrorBackoffTime: () -> Duration? = errorBackoffTime ?: { null }

        return PrefetchingMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                object : PrefetchingMessageRetrieverProperties {
                    override fun getDesiredMinPrefetchedMessages(): Int = actualDesiredPrefetchedMessages

                    override fun getMaxPrefetchedMessages(): Int = actualMaxPrefetched

                    override fun getMessageVisibilityTimeout(): Duration? = actualMessageVisibility()

                    override fun getErrorBackoffTime(): Duration? = actualErrorBackoffTime()
                }
        )
    }
}

fun prefetchingMessageRetriever(sqsAsyncClient: SqsAsyncClient, queueProperties: QueueProperties, init: PrefetchingMessageRetrieverDslBuilder.() -> Unit)
        = initComponent(PrefetchingMessageRetrieverDslBuilder(sqsAsyncClient, queueProperties), init)
