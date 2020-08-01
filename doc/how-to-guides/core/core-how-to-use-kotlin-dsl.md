# Core - Kotlin DSL

As the [core](../../../core) library can be quite verbose to configure
a [MessageListenerContainer](../../../api/src/main/java/com/jashmore/sqs/container/MessageListenerContainer.java),
the [Core Kotlin DSL](../../../extensions/core-kotlin-dsl) tool can be used to easily set up a message listener.

## Steps

1. Depend on the [Core Kotlin DSL](../../../extensions/core-kotlin-dsl) module:

    ```kotlin
    implementation("com.jashmore:core-kotlin-dsl:${version}")
    ```

   or

    ```xml
    <dependency>
        <groupId>com.jashmore</groupId>
        <artifactId>core-kotlin-dsl</artifactId>
        <version>${version}</version>
    </dependency>
    ```

1. Create the [MessageListenerContainer](../../../api/src/main/java/com/jashmore/sqs/container/MessageListenerContainer.java) using the Kotlin DSL

    ```kotlin
        val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
            retriever = prefetchingMessageRetriever {
                desiredPrefetchedMessages = 10
                maxPrefetchedMessages = 20
            }
            processor = coreProcessor {
                argumentResolverService = coreArgumentResolverService(objectMapper)
                bean = MessageListener()
                method = MessageListener::class.java.getMethod("listen", String::class.java)
            }
            broker = concurrentBroker {
                concurrencyLevel = { 10 }
                concurrencyPollingRate = { Duration.ofSeconds(30) }
            }
            resolver = batchingResolver {
                bufferingSizeLimit = { 5 }
                bufferingTime = { Duration.ofSeconds(2) }
            }
        }
    ```

1. Start the container as normal

    ```kotlin
        container.start()
    ```

Check out the [Core Kotlin DSL](../../../extensions/core-kotlin-dsl) for more details about the internals of this module and what you can use.

## Using a lambda for the message processing

```kotlin
val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
    processor = lambdaProcessor {
        method { message ->
            log.info("Message received: {}", message.body())
        }
    }
    // other configuration
}
```

## Using the batchingMessageListener

This is equivalent to
the [@QueueListener](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/basic/QueueListener.java) annotation
used in a Spring Boot application which will set up a container that will request for messages in batches.

```kotlin
val container = batchingMessageListener("identifier", sqsAsyncClient, "url") {
    concurrencyLevel = { 10 }
    batchSize = { 5 }
    batchingPeriod =  { Duration.ofSeconds(5) }

    processor = lambdaProcessor {
        method { message ->
            log.info("Message: {}", message.body())
        }
    }
}
```

## Using the prefetchingMessageListener

This is equivalent to
the [@PrefetchingQueueListener](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/prefetch/PrefetchingQueueListener.java) annotation
used in a Spring Boot application which will set up a container that will prefetch messages for processing.

```kotlin
val container = prefetchingMessageListener("identifier", sqsAsyncClient, "url") {
    concurrencyLevel = { 2 }
    desiredPrefetchedMessages = 5
    maxPrefetchedMessages = 10

    processor = lambdaProcessor {
        method { message ->
            log.info("Message: {}", message.body())
        }
    }
}
```

## Example

A full example of using the Kotlin DSL can be found in the [core-kotlin-example](../../../examples/core-kotlin-example/README.md).
