# Core Example

This example shows the usage of the [core](../../core) library to create a message listener that dynamically changes the rate
of message processing concurrency. As the application runs, in a fixed interval the concurrency rate will randomly change to a different level showing
that the message listener can be changed based on some business logic that you define.

## Usage

Run the main function from an IDE or in the terminal run:

```bash
gradle runApp
```

## Kotlin DSL alternative

If you are using Kotlin, the [core-kotlin-example](../core-kotlin-example) shows an equivalent application that uses
the [Kotlin Core DSL](../../extensions/core-kotlin-dsl) to build the message listener in a less verbose way.
