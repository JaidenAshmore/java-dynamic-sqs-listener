# Core Implementations
This document provides a quick overview of the core implementations of the API that can be used. Note that consumer can define their own implementations and
are not required to use these core implementations.

## Architecture
The following is the diagram for how a single message would be processed through the library.

![Core Framework Architecture Diagram](./resources/architecture_diagram.png "Core Framework Architecture Diagram")

### Message Retriever
The [MessageRetriever](../api/src/main/java/com/jashmore/sqs/retriever) has a simple API in that it only exposes methods to obtain
a message from the queue and it makes no requirements on how it should get these messages from the SQS queue. This allows for ability to optimise the
retrieval of messages such as batching requests for retrieving messages or pre-fetching messages.

Core implementations include:
- [PrefetchingMessageRetriever](../core/src/main/java/com/jashmore/sqs/retriever/prefetch/PrefetchingMessageRetriever.java):
this will prefetch messages from the queue so that new messages can be processed as soon as possible. This implementation is not appropriate if the
prefetched message's visibility timeout expires before it can be picked up for process due to the message processing of previous messages taking too long.
The result is that if the message has a re-drive policy it will be placed back into the queue and processed multiple times. This implementation is appropriate
for high volumes of messages that take little time to process.
- [BatchingMessageRetriever](../core/src/main/java/com/jashmore/sqs/retriever/batching/BatchingMessageRetriever.java):
This will batch requests for messages from the consumer into a single call out to the SQS queue once a certain threshold of messages were requested or at 
a given period if this threshold is not reached. This reduces the number of calls out to the SQS queue but reduces the performance
as messages are not being requested while the batch is waiting to be built.

### Message Processor
The [MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java)
has the responsibility of processing the message that has been retrieved by the[MessageRetriever](../api/src/main/java/com/jashmore/sqs/retriever)
by calling the corresponding Java method that processes the message.  This should resolve any arguments for the method by calling to the
[ArgumentResolverService](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) and using the resulting
values as the method arguments.

Core implementations include:
- [CoreMessageProcessor](../core/src/main/java/com/jashmore/sqs/processor/CoreMessageProcessor.java):
default implementation that calls out to a `ArgumentResolverService` to resolve the arguments and calls the method.

### ArgumentResolverService
The [ArgumentResolverService](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) is used to obtain the
[ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) that can be used to populate an argument
in a method when a message is being processed. For example, a parameter with the
[@Payload](../core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java) annotation will be resolved with the body
of the message cast to that type.

The implementations of the `ArgumentResolverService` include:
- [DelegatingArgumentResolverService](../core/src/main/java/com/jashmore/sqs/argument/DelegatingArgumentResolverService.java):
this implementation delegates to specific [ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s
that have been passed in. See below for the core
[ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s that are available.
- [CoreArgumentResolverService](../core/src/main/java/com/jashmore/sqs/argument/CoreArgumentResolverService.java): this is
a helper implementation that uses the DelegatingArgumentResolverService under the hood with all of the core
[ArgumentResolver](../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s.

The core arguments that be resolved include:
- `software.amazon.awssdk.services.sqs.model.Message`: arguments that have the `Message` type will place the entire message that is being processed into
this argument. This is useful if you need to forward this message to other services or want to manually extract information from the service. This is
provided by the [MessageArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/message/MessageArgumentResolver.java).
- [@Payload](../core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java): arguments annotated with this will parse the
message body into that object. If this is a String a direct transfer of the message contents is passed in, otherwise if it is a Java Bean, an attempt to
cast the message body to that bean will be used. This is provided by the
[PayloadArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver.java), which uses
a [PayloadMapper](../core/src/main/java/com/jashmore/sqs/argument/payload/mapper/PayloadMapper.java), such as
the [JacksonPayloadMapper](../core/src/main/java/com/jashmore/sqs/argument/payload/mapper/JacksonPayloadMapper.java)
that uses a Jackson `ObjectMapper` to parse the message body.
- [@MessageId](../core/src/main/java/com/jashmore/sqs/argument/messageid/MessageId.java): string arguments annotated with this will
place the message ID of the message into this argument. This is provided by the
[MessageIdArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/messageid/MessageIdArgumentResolver.java).
- [Acknowledge](../api/src/main/java/com/jashmore/sqs/processor/argument/Acknowledge.java): arguments of this type will be injected
with an implementation that allows for a message to be manually acknowledged when it is successfully processed. Note that if this is included in the messages
signature, the [MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) is not required to
acknowledge the message after a successful execution. These implementations should be provided by the
[MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) being used.
- [MessageAttribute](../core/src/main/java/com/jashmore/sqs/argument/attribute/MessageAttribute.java): arguments annotated with this
will attempt to parse the contents of the message attribute into this field. For example, if the argument is a String then the attribute will be cast to a
string where as if the argument is an integer it will try and parse the string into the number.  This also works with POJOs in that the resolver will
 attempt to deserialised the message attribute into this POJO shape, e.g. via the Jackson Object Mapper.  This is provided by the
[MessageAttributeArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/attribute/MessageAttributeArgumentResolver.java).
- [MessageSystemAttribute](../core/src/main/java/com/jashmore/sqs/argument/attribute/MessageAttribute.java); arguments annotated
with this will attempt to parse the contents of a system message attribute into this field. For example, the `SENT_TIMESTAMP` of the message can be obtained
by this annotation.  This is provided by the
[MessageSystemAttributeArgumentResolver](../core/src/main/java/com/jashmore/sqs/argument/attribute/MessageSystemAttributeArgumentResolver.java).
- [VisibilityExtender](../api/src/main/java/com/jashmore/sqs/processor/argument/VisibilityExtender.java): arguments of this type
will be injected with an implementation that extends the message visibility of the current message.  These implementations should be provided by the
[MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) being used.

### Message Broker
The [MessageBroker](../api/src/main/java/com/jashmore/sqs/broker/MessageBroker.java) is the main container that controls the
whole flow of messages from the [MessageRetriever](../api/src/main/java/com/jashmore/sqs/retriever) to the
[MessageProcessor](../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java). It can provide logic like the rate
of concurrency of the messages being processed or when messages should be processed.

Core implementation include:
- [ConcurrentMessageBroker](../core/src/main/java/com/jashmore/sqs/broker/concurrent/ConcurrentMessageBroker.java): this
implementation will run on multiple threads each processing messages. It has dynamic configuration and this allows the rate of concurrency to change
dynamically while the application is running.

### Message Resolver
The [MessageResolver](../api/src/main/java/com/jashmore/sqs/resolver/MessageResolver.java) is used when the message has been
successfully processed and it is needed to be removed from the SQS queue so it isn't processed again.

Core implementation include:
- [BatchingMessageResolver](../core/src/main/java/com/jashmore/sqs/resolver/batching/BatchingMessageResolver.java): this
implementation will batch calls to delete messages from the SQS queue into a batch that will go out together once asynchronously. This is useful if you
are processing many messages at the same time and it is desirable to reduce the number of calls out to SQS. A disadvantage is that the message may
sit in the batch for enough time that the visibility expires and it is placed onto the queue. To mitigate this, smaller batch
timeout should be used or by increasing the visibility timeout. Note you can configure this to always delete a message as soon as it is finished by
setting the batch size of 1.
