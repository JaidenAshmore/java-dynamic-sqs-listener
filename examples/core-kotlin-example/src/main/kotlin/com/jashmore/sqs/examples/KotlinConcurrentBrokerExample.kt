@file:JvmName("KotlinConcurrentBrokerExample")
package com.jashmore.sqs.examples

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.propagation.ThreadLocalCurrentTraceContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jashmore.sqs.argument.messageid.MessageId
import com.jashmore.sqs.argument.payload.Payload
import com.jashmore.sqs.core.kotlin.dsl.argument.coreArgumentResolverService
import com.jashmore.sqs.core.kotlin.dsl.container.coreMessageListener
import com.jashmore.sqs.core.kotlin.dsl.utils.cached
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient
import com.jashmore.sqs.extensions.brave.decorator.BraveMessageProcessingDecorator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private val log: Logger = LoggerFactory.getLogger("example")
private val concurrencyLevelPeriod = Duration.ofSeconds(5)
private const val concurrencyLimit = 10

private val objectMapper = ObjectMapper().registerModule(KotlinModule())

fun main() {
    val sqsAsyncClient = ElasticMqSqsAsyncClient()
    val queueUrl = sqsAsyncClient.createRandomQueue()
            .get()
            .queueUrl()

    val tracing = Tracing.newBuilder()
            .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
                    .addScopeDecorator(MDCScopeDecorator.get())
                    .build()
            )
            .build()
    tracing.isNoop = true

    val container = coreMessageListener("core-example-container", sqsAsyncClient, queueUrl) {
        retriever = prefetchingMessageRetriever {
            desiredPrefetchedMessages = 10
            maxPrefetchedMessages = 20
        }
        processor = coreProcessor {
            argumentResolverService = coreArgumentResolverService(objectMapper)
            bean = MessageListener()
            method = MessageListener::class.java.getMethod("listen", Request::class.java, String::class.java)
            decorators {
                add(BraveMessageProcessingDecorator(tracing))
            }
        }
        broker = concurrentBroker {
            concurrencyLevel = cached(Duration.ofSeconds(10)) {
                Random.nextInt(concurrencyLimit)
            }
            concurrencyPollingRate = { concurrencyLevelPeriod }
            errorBackoffTime = { Duration.ofMinutes(500) }
        }
        resolver = batchingResolver {
            bufferingSizeLimit = { 1 }
            bufferingTime = { Duration.ofSeconds(5) }
        }
    }

    container.start()

    log.info("Started container")

    CompletableFuture.runAsync {
        messageProducer(sqsAsyncClient, queueUrl)
    }
}

data class Request(val key: String)

fun messageProducer(sqsAsyncClient: SqsAsyncClient, queueUrl: String) {
    val count = AtomicInteger(0)
    while (!Thread.currentThread().isInterrupted) {
        try {
            sqsAsyncClient.sendMessageBatch { batchBuilder ->
                batchBuilder.queueUrl(queueUrl)
                        .entries((0..9).map { index ->
                            val request = Request("key_${count.getAndIncrement()}")
                            SendMessageBatchRequestEntry.builder()
                                    .id("$index")
                                    .messageBody(objectMapper.writeValueAsString(request))
                                    .build()
                        })
            }
            log.info("Put 10 messages onto queue")
            Thread.sleep(2000)
        } catch (interruptedException: InterruptedException) {
            log.info("Producer Thread has been interrupted")
            break
        }
    }
}

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
