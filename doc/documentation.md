# Documentation

This contains all of the documentation for the framework.

## Table of Contents

1. [Core Implementation Overview](core-implementations-overview.md): this document provides a quick overview of the libraries core implementations. For a
more in depth understanding take a look at the JavaDoc for the [java-dynamic-sqs-listener-api](../api/src/main/java/com/jashmore/sqs).
1. How to Guides:
    1. Core Framework How To Guides
        1. [How to implement a custom MessageRetriever](how-to-guides/core/core-how-to-implement-a-custom-message-retrieval.md): useful for changing the logic
        for obtaining messages from the SQS queue if the core implementations do not provided the required functionality
        1. [How to implement a custom ArgumentResolver](how-to-guides/core/core-how-to-implement-a-custom-argument-resolver.md): useful for changing how the
        arguments in the method being executed are resolved
        1. [How to add Brave Tracing](how-to-guides/core/core-how-to-add-brave-tracing.md): for including Brave Tracing information to your messages
        1. [How to extend a message's visibility during processing](how-to-guides/core/core-how-to-extend-message-visibility-during-processing.md): useful for
        extending the visibility of a message in the case of long processing so it does not get put back on the queue while processing
        1. [How to manually acknowledge message](how-to-guides/core/core-how-to-mark-message-as-successfully-processed.md): useful for when you want to mark the
        message as successfully processed before the method has finished executing
        1. [How to create a MessageProcessingDecorator](how-to-guides/core/core-how-to-create-a-message-processing-decorator.md): guide for writing your own
        decorator to wrap a message listener's processing of a message
    1. [How to Connect to an AWS SQS Queue](how-to-guides/how-to-connect-to-aws-sqs-queue.md): necessary for actually using this framework in live environments
    1. Spring How To Guides
        1. [How to add a custom ArgumentResolver to a Spring application](how-to-guides/spring/spring-how-to-add-custom-argument-resolver.md): useful for
        integrating custom argument resolution code to be included in a Spring Application. See [How to implement a custom ArgumentResolver](how-to-guides/core/core-how-to-implement-a-custom-argument-resolver.md)
        for how build a new ArgumentResolver from scratch
        1. [How to add custom MessageProcessingDecorators](how-to-guides/spring/spring-how-to-add-custom-message-processing-decorators.md): guide on how
        to autowire custom `MessageProcessingDecorators` into your Spring Queue Listeners.
        1. [How to add Brave Tracing](how-to-guides/spring/spring-how-to-add-brave-tracing.md): for including Brave Tracing information to your messages
        1. [How to provide a custom Object Mapper](how-to-guides/spring/spring-how-to-add-custom-argument-resolver.md): guide for overriding the default
        `ObjectMapper` that is used to serialise the message body and attributes
        1. [How to add your own queue listener](how-to-guides/spring/spring-how-to-add-own-queue-listener.md): useful for defining your own annotation for the
        queue listening without the verbosity of a custom queue listener
        1. [How to write Spring Integration Tests](how-to-guides/spring/spring-how-to-write-integration-tests.md): you actually want to test what you are
        writing right?
        1. [How to Start/Stop Queue Listeners](how-to-guides/spring/spring-how-to-start-stop-queue-listeners.md): guide for starting and stopping the
        processing of messages for specific queue listeners
        1. [How to connect to multiple AWS Accounts](how-to-guides/spring/spring-how-to-connect-to-multiple-aws-accounts.md): guide for listening to queues
        across multiple AWS Accounts
        1. [How to version message payload schemas](how-to-guides/spring/spring-how-to-version-payload-schemas-using-spring-cloud-schema-registry.md): guide
        for versioning payloads using Avro and the Spring Cloud Schema Registry.
1. Local Development:
    1. [Setting up IntelliJ](local-development/setting-up-intellij.md): steps for setting IntelliJ up for development,
    e.g. configuring checkstyle, Lombok, etc
    1. [Releasing Artifacts](local-development/release-artifact.md): reminder steps for the project owner on how to
    release a version of this library
