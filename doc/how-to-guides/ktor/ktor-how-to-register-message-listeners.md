# Ktor - How to Register Message Listeners

The [Ktor Core](../../../ktor/core) module adds the ability to register Message Listeners into the web application.

## Steps

1. Include the [Ktor Core](../../../ktor/core) dependency.

    ```xml
    <dependency>
        <groupId>com.jashmore</groupId>
        <artifactId>java-dynamic-sqs-listener-ktor-core</artifactId>
        <version>${dynamic-sqs-listener.version}</version>
    </dependency>
    ```

1. Include one of the message listener [extension functions](../../../ktor/core/src/main/kotlin/com/jashmore/sqs/ktor/container/KtorCoreExtension.kt) imports.

    ```kotlin
    import com.jashmore.sqs.ktor.container.* // Change * to the specific function
    ```

    Make sure to use this `ktor` import instead of the `com.jashmore.sqs.core.kotlin.dsl.container.*` import as the `ktor` version will attach
    to the lifecycle of the server.

1. Integrate the message listener into the web application

    ```kotlin
    val server = embeddedServer(Netty, 8080) {
        batchingMessageListener("batching-listener", sqsAsyncClient, queeuUrl) {
            concurrencyLevel = { 2 }

            processor = lambdaProcessor {
                method { message ->
                    log.info("Processing message: ${message.body()}")
                }
            }
        }
    }
    ```

## Shutting down the container on server shutdown

To make sure the message listener is gracefully shutdown when the web server is shutdown, you will need to make sure to
attach to a `ShutdownHook`:

```kotlin
    val server = embeddedServer(Netty, 8080) {
       // setup message listeners here
    }
    server.start()
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(1, 30_000)
    })
    Thread.currentThread().join()
```
