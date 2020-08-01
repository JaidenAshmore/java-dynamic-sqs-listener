# Ktor Example

This example shows the usage of the [Ktor Core](../../ktor/core) module which uses the [Core Kotlin DSL](../../extensions/core-kotlin-dsl) to build
the message listeners. It creates multiple message listeners that use different methods to process messages, such as prefetching messages, etc.

## Usage

```bash
gradle runApp
```

Perform a GET request to [http://localhost:8080/message/{queueIdentifier}](http://localhost:8080/message/one) where the queueIdentifier is one
of `one`, `two`, or `three`. Any unknown queue will return 404. You can also start and stop the message listeners by navigating to
[http://localhost:8080/start/{queueIdentifier}](http://localhost:8080/start/one) and
[http://localhost:8080/stop/{queueIdentifier}](http://localhost:8080/stop/one) respectively.
