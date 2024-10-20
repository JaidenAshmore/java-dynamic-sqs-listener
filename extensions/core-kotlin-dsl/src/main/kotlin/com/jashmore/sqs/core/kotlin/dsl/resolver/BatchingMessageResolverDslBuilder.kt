package com.jashmore.sqs.core.kotlin.dsl.resolver

import com.jashmore.sqs.QueueProperties
import com.jashmore.sqs.core.kotlin.dsl.MessageListenerComponentDslMarker
import com.jashmore.sqs.core.kotlin.dsl.MessageResolverDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.initComponent
import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import com.jashmore.sqs.resolver.MessageResolver
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver
import com.jashmore.sqs.resolver.batching.BatchingMessageResolverProperties
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.time.Duration

/**
 * [MessageResolverDslBuilder] that will construct a [BatchingMessageResolver] for usage in this container.
 */
@MessageListenerComponentDslMarker
class BatchingMessageResolverDslBuilder(
    private val sqsAsyncClient: SqsAsyncClient,
    private val queueProperties: QueueProperties
) : MessageResolverDslBuilder {
    /**
     * Supplier for getting the buffer size for resolving the messages.
     *
     * @see [BatchingMessageResolverProperties.getBufferingSizeLimit] for in-depth details about this field
     */
    var batchSize: (() -> Int)? = null

    /**
     * Supplier for getting the amount of time to wait for the buffer to fill to the limit before sending out any currently buffered messages.
     *
     * @see [BatchingMessageResolverProperties.getBufferingTime] for in-depth details about this field
     */
    var batchingPeriod: (() -> Duration)? = null

    override fun invoke(): MessageResolver {
        val actualBatchSize = batchSize
        val actualBatchingPeriod = batchingPeriod

        if (actualBatchSize == null && actualBatchingPeriod == null) {
            return BatchingMessageResolver(
                queueProperties,
                sqsAsyncClient
            )
        } else if (actualBatchSize != null && actualBatchingPeriod != null) {
            return BatchingMessageResolver(
                queueProperties,
                sqsAsyncClient,
                object : BatchingMessageResolverProperties {
                    override fun getBufferingSizeLimit(): Int = actualBatchSize()

                    override fun getBufferingTime(): Duration = actualBatchingPeriod()
                }
            )
        } else if (actualBatchSize == null) {
            throw RequiredFieldException("batchSize", "BatchingMessageResolver")
        } else {
            throw RequiredFieldException("batchingPeriod", "BatchingMessageResolver")
        }
    }
}

fun batchingResolver(sqsAsyncClient: SqsAsyncClient, queueProperties: QueueProperties, init: BatchingMessageResolverDslBuilder.() -> Unit) =
    initComponent(BatchingMessageResolverDslBuilder(sqsAsyncClient, queueProperties), init)
