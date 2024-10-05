@file:JvmName("KotlinConcurrentBrokerExample")
@file:Suppress("MatchingDeclarationName", "MagicNumber")
package com.jashmore.sqs.examples

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.propagation.ThreadLocalCurrentTraceContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jashmore.sqs.argument.messageid.MessageId
import com.jashmore.sqs.argument.payload.Payload
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker
import com.jashmore.sqs.core.kotlin.dsl.argument.coreArgumentResolverService
import com.jashmore.sqs.core.kotlin.dsl.container.coreMessageListener
import com.jashmore.sqs.core.kotlin.dsl.utils.cached
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient
import com.jashmore.sqs.extensions.brave.decorator.BraveMessageProcessingDecorator
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private val log: Logger = LoggerFactory.getLogger("example")
private val CONCURRENCY_LEVEL_PERIOD = Duration.ofSeconds(5)
private const val CONCURRENCY_LIMIT = 10

private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

/**
 * This example shows the core framework being used to processing messages place onto the queue with a dynamic level of concurrency via the
 * [ConcurrentMessageBroker]. The rate of concurrency is randomly changed every 10 seconds to a new value between 0 and 10.
 *
 * <p>This example will also show how the performance of the message processing can be improved by prefetching messages via the
 * [PrefetchingMessageRetriever].
 *
 * <p>While this is running you should see the messages being processed and the number of messages that are concurrently being processed. This will highlight
 * how the concurrency can change during the running of the application.
 */
@Suppress("LongMethod")
fun main() {
    val sqsAsyncClient = ElasticMqSqsAsyncClient()
    val queueUrl = sqsAsyncClient.createRandomQueue()
        .get()
        .queueUrl()

    val tracing = Tracing.newBuilder()
        .currentTraceContext(
            ThreadLocalCurrentTraceContext.newBuilder()
                .addScopeDecorator(MDCScopeDecorator.get())
                .build()
        )
        .build()
    tracing.isNoop = true

    val container = coreMessageListener("core-example-container", sqsAsyncClient, queueUrl) {
        broker = concurrentBroker {
            concurrencyLevel = cached(Duration.ofSeconds(10)) {
                Random.nextInt(CONCURRENCY_LIMIT)
            }
            concurrencyPollingRate = { CONCURRENCY_LEVEL_PERIOD }
            errorBackoffTime = { Duration.ofMillis(500) }
        }
        retriever = prefetchingMessageRetriever {
            desiredPrefetchedMessages = 10
            maxPrefetchedMessages = 20
        }
        processor = coreProcessor {
            decorators = listOf(BraveMessageProcessingDecorator(tracing))
            argumentResolverService = coreArgumentResolverService(objectMapper)
            bean = MessageListener()
            method = MessageListener::class.java.getMethod("listen", Request::class.java, String::class.java)
        }
        resolver = batchingResolver {
            batchSize = { 1 }
            batchingPeriod = { Duration.ofSeconds(5) }
        }
    }

    container.start()

    log.info("Started container")

    val count = AtomicInteger(0)
    val scheduledExecutorService = Executors.newScheduledThreadPool(1)
    scheduledExecutorService.scheduleAtFixedRate(
        {
            sqsAsyncClient.sendMessageBatch { batchBuilder ->
                batchBuilder.queueUrl(queueUrl)
                    .entries(
                        (0..9).map { index ->
                            val request = Request("key_${count.getAndIncrement()}")
                            SendMessageBatchRequestEntry.builder()
                                .id("$index")
                                .messageBody(objectMapper.writeValueAsString(request))
                                .build()
                        }
                    )
            }
            log.info("Put 10 messages onto queue")
        },
        0,
        2,
        TimeUnit.SECONDS
    )

    log.info("Running application for 3 minutes. Ctrl + C to exit...")
    Thread.sleep(3 * 60 * 1000.toLong())
    scheduledExecutorService.shutdownNow()
}

data class Request(val key: String)

class MessageListener {
    private val concurrentMessagesBeingProcessed = AtomicInteger(0)

    @Suppress("unused")
    fun listen(@Payload request: Request, @MessageId messageId: String) {
        try {
            val concurrentMessages = concurrentMessagesBeingProcessed.incrementAndGet()
            val processingTimeInMs = Random.nextInt(3000)
            log.trace("Payload: {}, messageId: {}", request, messageId)
            log.info("Message processing in {}ms. {} currently being processed concurrently", processingTimeInMs, concurrentMessages)
            Thread.sleep(processingTimeInMs.toLong())
        } finally {
            concurrentMessagesBeingProcessed.decrementAndGet()
        }
    }
}
