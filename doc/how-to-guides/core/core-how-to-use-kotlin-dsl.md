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

    ``kotlin
        container.start()
    ``

Check out the [Core Kotlin DSL](../../../extensions/core-kotlin-dsl) for more details about the internals of this module and what you can use.

## Example
A full example of using the Kotlin DSL can be found in the [core-kotlin-example](../../../examples/core-kotlin-example/README.md).
