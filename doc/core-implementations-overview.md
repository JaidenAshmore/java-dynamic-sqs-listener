# Core Implementations

This document provides a quick overview of the core implementations of the API that can be used. Note that consumer can define their own implementations and
are not required to use these core implementations.

## Architecture

The following is the diagram for how a single message would be processed through the library.

![Core Framework Architecture Diagram](./resources/architecture_diagram.png "Core Framework Architecture Diagram")

### Message Retriever

The [MessageRetriever](../api/src/main/java/com/jashmore/sqs/retriever) has a simple API in that it only exposes methods to obtain
a message from the queue, and it makes no requirements on how it should get these messages from the SQS queue. This allows for ability to optimise the
retrieval of messages such as batching requests for retrieving messages or pre-fetching messages.

Core implementations include:

-   [PrefetchingMessageRetriever](../core/src/main/java/com/jashmore/sqs/retriever/prefetch/PrefetchingMessageRetriever.java):
    this will prefetch messages from the queue so that new messages can be processed as soon as possible. This implementation is not appropriate if the time
    to process messages is long enough for the prefetched message's visibility timeout to expire before it can be processed. In this scenario, the message's
    re-drive policy may place the message back into the queue resulting in it being processed multiple times. This implementation is appropriate
    for high volumes of messages that take little time to process.
-   [BatchingMessageRetriever](../core/src/main/java/com/jashmore/sqs/retriever/batching/BatchingMessageRetriever.java):
    This will batch requests for messages from the consumer into a single call out to the SQS queue once reaching the threshold of messages, or the batching
    timeout expires. This reduces the number of calls out to the SQS queue but can reduce the performance as no messages processing will occur while waiting
    for the batch size to be reached.

### Message Processor

The [MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java)
has the responsibility of processing the message that has been retrieved by the[MessageRetriever](../api/src/main/java/com/jashmore/sqs/retriever)
by calling the corresponding Java method that processes the message. This should resolve any arguments for the method by calling to the
[ArgumentResolverService](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) and using the resulting
values as the method arguments.

Core implementations include:

-   [CoreMessageProcessor](../core/src/main/java/com/jashmore/sqs/processor/CoreMessageProcessor.java):
    default implementation that calls out to a `ArgumentResolverService` to resolve the arguments and calls the method.
-   [DecoratingMessageProcessor](../core/src/main/java/com/jashmore/sqs/processor/DecoratingMessageProcessor.java): implementation that allows for the
    message processing to be decorated with [MessageProcessingDecorator](../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java) logic.
    This can be useful for adding tracing, metrics or other extra functionality in the message processing.
-   [LambdaMessageProcessor](../core/src/main/java/com/jashmore/sqs/processor/LambdaMessageProcessor.java): implementation that
    will use a lambda/functional synchronous methods to process the message. This does not support any argument resolution using an `ArgumentResolverService`.
-   [AsyncLambdaMessageProcessor](../core/src/main/java/com/jashmore/sqs/processor/AsyncLambdaMessageProcessor.java): implementation that
    will use a lambda/functional asynchronous methods (returns a `CompletableFuture`) to process the message. This does not support any argument
    resolution using an `ArgumentResolverService`.

### ArgumentResolverService

The [ArgumentResolverService](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) is used to obtain the
[ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) that can be used to populate an argument
in a method when processing a message. For example, a parameter with the
[@Payload](../core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java) annotation will be resolved with the body
of the message cast to that type.

The implementations of the `ArgumentResolverService` include:

-   [DelegatingArgumentResolverService](../core/src/main/java/com/jashmore/sqs/argument/DelegatingArgumentResolverService.java):
    this implementation delegates to specific [ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s
    that have been passed in. See below for the core
    [ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s that are available.
-   [CoreArgumentResolverService](../core/src/main/java/com/jashmore/sqs/argument/CoreArgumentResolverService.java): this is
    a helper implementation that uses the DelegatingArgumentResolverService under the hood with the core
    [ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s.

The core arguments that be resolved include:

-   `software.amazon.awssdk.services.sqs.model.Message`: arguments that have the `Message` type will place the entire message that is being processed into
    this argument. This is useful if you need to forward this message to other services or want to manually extract information from the service. This is
    provided by the [MessageArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/message/MessageArgumentResolver.java).
-   [@Payload](../core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java): arguments annotated with this will parse the
    message body into that object. If this is a String, the raw message body will be provided, otherwise if it is a Java Bean, an attempt to
    cast the message body to that bean will be used. This is provided by the
    [PayloadArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver.java) which uses
    a [PayloadMapper](../core/src/main/java/com/jashmore/sqs/argument/payload/mapper/PayloadMapper.java), such as
    the [JacksonPayloadMapper](../core/src/main/java/com/jashmore/sqs/argument/payload/mapper/JacksonPayloadMapper.java), to parse the message body.
-   [@MessageId](../core/src/main/java/com/jashmore/sqs/argument/messageid/MessageId.java): string arguments annotated with this will
    place the message ID of the message into this argument. This is provided by the
    [MessageIdArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/messageid/MessageIdArgumentResolver.java).
-   [Acknowledge](../api/src/main/java/com/jashmore/sqs/processor/argument/Acknowledge.java): arguments of this type will be injected
    with an implementation that allows for a message to be manually acknowledged when it is successfully processed. Note that if this is included,
    the [MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) is not required to
    acknowledge the message after a successful execution and the consumer must acknowledge the message them self. The implementation of the
    [Acknowledge](../api/src/main/java/com/jashmore/sqs/processor/argument/Acknowledge.java) should be provided by the
    [MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) instead of
    an [ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java).
-   [@MessageAttribute](../core/src/main/java/com/jashmore/sqs/argument/attribute/MessageAttribute.java): arguments annotated with this
    will attempt to parse the contents of the message attribute into this field. For example, if the argument is a String then the attribute will be cast to a
    string where as if the argument is an integer it will try and parse the string into the number. This also works with POJOs in that the resolver will
    attempt to deserialised the message attribute into this POJO shape, e.g. via the Jackson Object Mapper. This is provided by the
    [MessageAttributeArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/attribute/MessageAttributeArgumentResolver.java).
-   [@MessageSystemAttribute](../core/src/main/java/com/jashmore/sqs/argument/attribute/MessageAttribute.java): arguments annotated
    with this will attempt to parse the contents of a system message attribute into this field. For example, the `SENT_TIMESTAMP` of the message can be obtained
    by this annotation. This is provided by the
    [MessageSystemAttributeArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/attribute/MessageSystemAttributeArgumentResolver.java).
-   [VisibilityExtender](../api/src/main/java/com/jashmore/sqs/processor/argument/VisibilityExtender.java): arguments of this type
    will be injected with an implementation that extends the message visibility of the current message. These implementations should be provided by the
    [MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) instead of
    an [ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java).

### Message Broker

The [MessageBroker](../api/src/main/java/com/jashmore/sqs/broker/MessageBroker.java) is the main container that controls the
whole flow of messages from the [MessageRetriever](../api/src/main/java/com/jashmore/sqs/retriever) to the
[MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java). It can provide logic like the rate
of concurrency of messages processing or when messages should be processed.

Core implementation include:

-   [ConcurrentMessageBroker](../core/src/main/java/com/jashmore/sqs/broker/concurrent/ConcurrentMessageBroker.java): this
    implementation will run on multiple threads each processing messages. It allows the configuration to be changed dynamically, such as changing the rate of
    concurrency to change while the application is running.

### Message Resolver

The [MessageResolver](../api/src/main/java/com/jashmore/sqs/resolver/MessageResolver.java) is used when the message has been
successfully processed, and it needs to be removed from the SQS queue.

Core implementation include:

-   [BatchingMessageResolver](../core/src/main/java/com/jashmore/sqs/resolver/batching/BatchingMessageResolver.java): this
    implementation will batch calls to delete messages from the SQS queue into a batch that will go out together once asynchronously. This is useful if you
    are processing many messages at the same time, and it is desirable to reduce the number of calls out to SQS. A disadvantage is that the message may
    sit in the batch for enough time for the visibility timeout to expire, and it is placed onto the queue again. To mitigate this, a smaller batch
    timeout should be used or by increasing the visibility timeout. Note that you can configure this to always delete a message as soon as it is finished by
    setting the batch size of 1.
