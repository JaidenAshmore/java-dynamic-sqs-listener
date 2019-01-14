# How-To Guides
To ease consumers using and extending this framework, these How-To guides have been provided.

1. Core Framework How To Guides
    1. [How to implement a custom ArgumentResolver](core/core-how-to-implement-a-custom-argument-resolver.md): useful for changing how the
    arguments in the method being executed are resolved
    1. [How to implement a custom MessageRetriever](core/core-how-to-implement-a-custom-message-retrieval.md): useful for changing the logic
    for obtaining messages from the SQS queue
1. Spring How To Guides
    1. [How to add a custom ArgumentResolver](spring/spring-how-to-add-custom-argument-resolver.md): useful for integrating custom argument
    resolution code to be included in a Spring Application
    1. [How to add a custom QueueWrapper](spring/spring-how-to-add-custom-queue-wrapper.md): useful for integrating custom components of the
    framework into annotation based queue listeners. E.g. creating a new MessageRetriever and using that via an annotation
    1. [How to write Spring Integration Tests](spring/spring-how-to-write-integration-tests.md): you actually want to test what you are
    writing right?
1. [How to Connect to an AWS SQS Queue](how-to-connect-to-aws-sqs-queue.md): necessary for actually using this framework in live environments
