package com.jashmore.sqs.ktor.container

import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.core.kotlin.dsl.container.BatchingMessageListenerContainerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.container.CoreMessageListenerContainerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.container.FifoMessageListenerContainerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.container.PrefetchingMessageListenerContainerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.container.coreMessageListener
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.KtorDsl
import software.amazon.awssdk.services.sqs.SqsAsyncClient

@KtorDsl
fun Application.messageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueUrl: String,
    config: CoreMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {
    return initMessageListener(environment, coreMessageListener(identifier, sqsAsyncClient, queueUrl, config))
}

@KtorDsl
fun Application.prefetchingMessageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueUrl: String,
    init: PrefetchingMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {
    return initMessageListener(environment, com.jashmore.sqs.core.kotlin.dsl.container.prefetchingMessageListener(identifier, sqsAsyncClient, queueUrl, init))
}

@KtorDsl
fun Application.batchingMessageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueUrl: String,
    init: BatchingMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {
    return initMessageListener(environment, com.jashmore.sqs.core.kotlin.dsl.container.batchingMessageListener(identifier, sqsAsyncClient, queueUrl, init))
}

@KtorDsl
fun Application.fifoMessageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueUrl: String,
    init: FifoMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {
    return initMessageListener(environment, com.jashmore.sqs.core.kotlin.dsl.container.fifoMessageListener(identifier, sqsAsyncClient, queueUrl, init))
}

fun initMessageListener(
    environment: ApplicationEnvironment,
    container: MessageListenerContainer
): MessageListenerContainer {
    val hook = Thread { container.stop() }
    environment.monitor.subscribe(ApplicationStarted) {
        environment.monitor.subscribe(ApplicationStopped) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook)
            } catch (alreadyShuttingDown: IllegalStateException) {
                // ignore
            }
            container.stop()
        }
        Runtime.getRuntime().addShutdownHook(hook)
        container.start()
    }

    return container
}
