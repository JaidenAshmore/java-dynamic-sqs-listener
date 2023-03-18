@file:JvmName("KtorApplicationExample")
@file:Suppress("MagicNumber")
package com.jashmore.sqs.examples

import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient
import com.jashmore.sqs.ktor.container.batchingMessageListener
import com.jashmore.sqs.ktor.container.messageListener
import com.jashmore.sqs.ktor.container.prefetchingMessageListener
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.sqs.model.Message
import java.time.Duration

@Suppress("LongMethod", "ComplexMethod")
fun main() {
    val sqsAsyncClient = ElasticMqSqsAsyncClient()

    val server = embeddedServer(Netty, 8080) {
        val firstQueueUrl = sqsAsyncClient.createRandomQueue().get().queueUrl()
        val firstContainer = messageListener("core-listener", sqsAsyncClient, firstQueueUrl) {
            processor = lambdaProcessor {
                method { message ->
                    log.info("Message: {}", message.body())
                }
            }
            retriever = batchingMessageRetriever {
                batchSize = { 1 }
                batchingPeriod = { Duration.ofSeconds(30) }
            }
            resolver = batchingResolver {
                batchSize = { 1 }
                batchingPeriod = { Duration.ofSeconds(5) }
            }
            broker = concurrentBroker {
                concurrencyLevel = { 5 }
                concurrencyPollingRate = { Duration.ofMinutes(1) }
            }
        }

        val secondQueueUrl = sqsAsyncClient.createRandomQueue().get().queueUrl()
        val secondContainer = prefetchingMessageListener("prefetching-listener", sqsAsyncClient, secondQueueUrl) {
            concurrencyLevel = { 2 }
            desiredPrefetchedMessages = 5
            maxPrefetchedMessages = 10

            processor = lambdaProcessor {
                methodWithVisibilityExtender { message, _ ->
                    log.info("Message: {}", message.body())
                }
            }
        }

        val thirdQueueUrl = sqsAsyncClient.createRandomQueue().get().queueUrl()
        val thirdContainer = batchingMessageListener("batching-listener", sqsAsyncClient, thirdQueueUrl) {
            concurrencyLevel = { 2 }
            batchSize = { 5 }
            batchingPeriod = { Duration.ofSeconds(5) }

            processor = asyncLambdaProcessor {
                method { message ->
                    log.info("Processing message: ${message.body()}")
                    future {
                        something(message)
                    }
                }
            }
        }

        routing {
            get("/message/{queue}") {
                val queueIdentifier = call.parameters["queue"]
                val queueUrl: String? = when (queueIdentifier) {
                    "one" -> firstQueueUrl
                    "two" -> secondQueueUrl
                    "three" -> thirdQueueUrl
                    else -> null
                }

                if (queueUrl == null) {
                    call.respond(HttpStatusCode.NotFound, "Unknown queue: $queueIdentifier")
                    return@get
                }

                val response = sqsAsyncClient.sendMessage {
                    it.queueUrl(queueUrl).messageBody("body")
                }.await()

                call.respondText("Hello, world! ${response.messageId()}", ContentType.Text.Html)
            }

            get("/start/{queue}") {
                val queueIdentifier = call.parameters["queue"]
                val container: MessageListenerContainer? = when (queueIdentifier) {
                    "one" -> firstContainer
                    "two" -> secondContainer
                    "three" -> thirdContainer
                    else -> null
                }

                if (container == null) {
                    call.respond(HttpStatusCode.NotFound, "Unknown queue: $queueIdentifier")
                    return@get
                }

                container.start()

                call.respondText("Container $queueIdentifier started", ContentType.Text.Html)
            }

            get("/stop/{queue}") {
                val queueIdentifier = call.parameters["queue"]
                val container: MessageListenerContainer? = when (queueIdentifier) {
                    "one" -> firstContainer
                    "two" -> secondContainer
                    "three" -> thirdContainer
                    else -> null
                }

                if (container == null) {
                    call.respond(HttpStatusCode.NotFound, "Unknown queue: $queueIdentifier")
                    return@get
                }

                container.stop()

                call.respondText("Container $queueIdentifier stopped", ContentType.Text.Html)
            }
        }
    }
    server.start(wait = true)
}

suspend fun something(@Suppress("UNUSED_PARAMETER") message: Message) = GlobalScope.launch {
    delay(5000)
}
