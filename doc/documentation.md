# Documentation
This contains all of the documentation for the framework.

## Table of Contents

1. [Core Implementation Overview](core-implementations-overview.md): this document provides a quick overview of the libraries core implementations. For a
more in depth understanding take a look at the JavaDoc for the API.
1. How to Guides:
    1. Core Framework How To Guides
        1. [How to implement a custom ArgumentResolver](how-to-guides/core/core-how-to-implement-a-custom-argument-resolver.md): useful for changing how the
        arguments in the method being executed are resolved
        1. [How to implement a custom MessageRetriever](how-to-guides/core/core-how-to-implement-a-custom-message-retrieval.md): useful for changing the logic
        for obtaining messages from the SQS queue
    1. Spring How To Guides
        1. [How to add a custom ArgumentResolver](how-to-guides/spring/spring-how-to-add-custom-argument-resolver.md): useful for integrating custom argument
        resolution code to be included in a Spring Application
        1. [How to add use the custom queue listener](how-to-guides/spring/spring-how-to-use-custom-queue-listener.md): useful for learning how to use custom
        components of the framework in a queue listener without creating your own annotation
        1. [How to add your own queue listener](how-to-guides/spring/spring-how-to-add-own-queue-listener.md): useful for defining your own annotation for
        queue listening without the verbosity of the custom queue listener
        1. [How to write Spring Integration Tests](how-to-guides/spring/spring-how-to-write-integration-tests.md): you actually want to test what you are
        writing right?
    1. [How to Connect to an AWS SQS Queue](how-to-guides/how-to-connect-to-aws-sqs-queue.md): necessary for actually using this framework in live environments
1. Local Development:
    1. [Setting up IntelliJ](local-development/setting-up-intellij.md): steps for setting IntelliJ up for development,
    e.g. configuring checkstyle, Lombok, etc
    1. [Releasing Artifacts](local-development/release-artifact.md): reminder steps for the project owner on how to
    release a version of this library
   
