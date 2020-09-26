package com.jashmore.sqs.ktor.container

import com.jashmore.sqs.container.MessageListenerContainer
import com.jashmore.sqs.core.kotlin.dsl.container.BatchingMessageListenerContainerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.container.CoreMessageListenerContainerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.container.FifoMessageListenerContainerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.container.PrefetchingMessageListenerContainerDslBuilder
import com.jashmore.sqs.core.kotlin.dsl.container.coreMessageListener
import io.ktor.application.Application
import io.ktor.application.ApplicationEnvironment
import io.ktor.application.ApplicationStarted
import io.ktor.application.ApplicationStopped
import io.ktor.util.pipeline.ContextDsl
import software.amazon.awssdk.services.sqs.SqsAsyncClient

@ContextDsl
fun Application.messageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueUrl: String,
    config: CoreMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {
    return initMessageListener(environment, coreMessageListener(identifier, sqsAsyncClient, queueUrl, config))
}

@ContextDsl
fun Application.prefetchingMessageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueUrl: String,
    init: PrefetchingMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {
    return initMessageListener(environment, com.jashmore.sqs.core.kotlin.dsl.container.prefetchingMessageListener(identifier, sqsAsyncClient, queueUrl, init))
}

@ContextDsl
fun Application.batchingMessageListener(
    identifier: String,
    sqsAsyncClient: SqsAsyncClient,
    queueUrl: String,
    init: BatchingMessageListenerContainerDslBuilder.() -> Unit
): MessageListenerContainer {
    return initMessageListener(environment, com.jashmore.sqs.core.kotlin.dsl.container.batchingMessageListener(identifier, sqsAsyncClient, queueUrl, init))
}

@ContextDsl
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
    environment.monitor.subscribe(ApplicationStarted) {
        container.start()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        container.stop()
    }

    return container
}
