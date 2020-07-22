# Java Dynamic SQS Listener Core

This is the core implementation of the Dynamic SQS Listener API. This contains basic implementations that should cover a significant amount of use cases
but if it doesn't, the consumer can easily implement their own.

## Usage

See the [core-example](../examples/core-example) for examples of using this core code to listen to a queue in a Java application. If it is a Kotlin application,
the [core-kotlin-example](../examples/core-kotlin-example) uses a [Core Kotlin DSL](../extensions/core-kotlin-dsl) for constructing a core message listener.

## More Information

For more information you can look at the root project [README.md](../README.md) which provides more information about the architecture
of the application. The [API](../api) is also a good location to find more information about what each part of the framework is how
they interact with each other.
