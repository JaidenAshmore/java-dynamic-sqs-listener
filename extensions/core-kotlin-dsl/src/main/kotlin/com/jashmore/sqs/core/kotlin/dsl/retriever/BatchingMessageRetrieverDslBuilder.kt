package com.jashmore.sqs.core.kotlin.dsl.retriever

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.MessageRetrieverDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.retriever.MessageRetriever
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

/**
 * The [MessageRetrieverDslBuilder] hat will construct a [PrefetchingMessageRetriever] for usage in this container.
 */
@MessageListenerComponentDslMarker
class BatchingMessageRetrieverDslBuilder(
    private val sqsAsyncClient: SqsAsyncClient,
    private val queueProperties: QueueProperties
) : MessageRetrieverDslBuilder {
    /**
     * The batch size for the number of messages to receive at once
     *
     * @see BatchingMessageRetrieverProperties.getBatchSize for more details about this field
     */
    var batchSize: (() -> Int)? = null

    /**
     * The maximum amount of time to wait for the number of messages requested to reach the [BatchingMessageRetrieverDslBuilder.batchSize].
     *
     * @see BatchingMessageRetrieverProperties.getBatchingPeriod for more details about this field
     */
    var batchingPeriod: (() -> Duration)? = null

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
        val actualBatchSize: () -> Int = batchSize ?: throw RequiredFieldException("batchSize", "BatchingMessageRetriever")
        val actualBatchingPeriod: () -> Duration = batchingPeriod ?: throw RequiredFieldException("batchingPeriod", "BatchingMessageRetriever")
        val actualMessageVisibility: () -> Duration? = messageVisibility ?: { null }
        val actualErrorBackoffTime: () -> Duration? = errorBackoffTime ?: { null }

        return BatchingMessageRetriever(
            queueProperties,
            sqsAsyncClient,
            object : BatchingMessageRetrieverProperties {
                override fun getBatchSize(): Int = actualBatchSize()

                override fun getBatchingPeriod(): Duration = actualBatchingPeriod()

                override fun getMessageVisibilityTimeout(): Duration? = actualMessageVisibility()

                override fun getErrorBackoffTime(): Duration? = actualErrorBackoffTime()
            }
        )
    }
}

fun batchingMessageRetriever(sqsAsyncClient: SqsAsyncClient, queueProperties: QueueProperties, init: BatchingMessageRetrieverDslBuilder.() -> Unit) =
    initComponent(BatchingMessageRetrieverDslBuilder(sqsAsyncClient, queueProperties), init)
