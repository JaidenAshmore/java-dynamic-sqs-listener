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
        1. [How to add a custom QueueWrapper](how-to-guides/spring/spring-how-to-add-custom-queue-wrapper.md): useful for integrating custom components of the
        framework into annotation based queue listeners. E.g. creating a new MessageRetriever and using that via an annotation
        1. [How to write Spring Integration Tests](how-to-guides/spring/spring-how-to-write-integration-tests.md): you actually want to test what you are
        writing right?
    1. [How to Connect to an AWS SQS Queue](how-to-guides/how-to-connect-to-aws-sqs-queue.md): necessary for actually using this framework in live environments

