This document provides a quick overview of the framework but for a more in depth understanding take a look at the JavaDoc for the API.

## Architecture
As can be seen in [java-dynamic-sqs-listener-api](./java-dynamic-sqs-listener-api) the listener has been divided into
four main responsibilities. Each of these have been built with no reliance on each other and therefore the consumer
can use or interchange the implementations of any of these with ease.

![Core Framework Architecture Diagram](./resources/architecture_diagram.png "Core Framework Architecture Diagram")

### Message Retriever
The [MessageRetriever](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever) has a simple API in that it only exposes methods to obtain a message from the queue and it makes no requirements on how it should get these messages from the SQS queue. This allows for ability to optimise the retrieval of messages such as batching requests for retrieviging messages or pre-fetching messages. Note that the `MessageRetriever` must be thread safe as there could be multiple threads all requesting for messages to process.

Core implementations of the `MessageRetriever` include:
- [IndividualMessageRetriever](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/retriever/individual/IndividualMessageRetriever.java): This will request a single message from the queue every time a consumer needs it. This is the least optimal but simplest solution and would most likely only be useful for local development or with a queue that has infrequent messages.
- [PrefetchingMessageRetriever](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/retriever/prefetch/PrefetchingMessageRetriever.java): This will prefetch messages from the queue so that consumers can have a message straight away. This can reduce the time to get a message and therefore increase performance but is not appropriate if the prefetched messages visibility timeout expires because the messages take a long time to be processed. This implementation is more appropriate for high volumes of messages that take little time to process.
- [BatchingMessageRetriever](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/retriever/batching/BatchingMessageRetriever.java): This will batch requests for messages from the consumer into a single call out to the SQS queue once a certain threshold of threads are requesting messages or at a given period if the threshold is not reached. This reduces the number of calls out to the SQS queue but reduces the performance as threads are just waiting until other threads also request messages.

### Message Processor
The [MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java)
has the responsibility of processing the message that has been from the SQS queue by calling the corresponding Java method that processes the message. This should resolve any arguments for the method by calling to the [ArgumentResolverService](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) and using the resulting values in the method call.

Core implementations of the `MessageProcessor` include:
- [DefaultMessageProcessor](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/processor/DefaultMessageProcessor.java): default implementation that calls out to a `ArgumentResolverService` to resolve the arguments and calls the method.
- [RetryableMessageProcessor](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/processor/retryable/RetryableMessageProcessor.java): implementation that will retry if there was a failure to process the message (an exception was thrown). This is useful if there is no redrive policy on your queue and it is desirable to just try again.

### ArgumentResolverService
The [ArgumentResolverService](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) is used to populate the parameters of a method being executed by the MessageProcessor with details about the message. For example, a parameter with the [@Payload](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java) annotation will be resolved with the body of the message cast to that type.

The implementations of the `ArgumentResolverService` include:
- [DelegatingArgumentResolverService](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/DelegatingArgumentResolverService.java): this implementation delegates to specific [ArgumentResolver](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s that have the responsibility of handling an individual type of argument resolution. See below for the core `ArgumentResolver`s available.
- [DefaultArgumentResolverService](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/DefaultArgumentResolverService.java): this is a helper implementation that uses the DelegatingArgumentResolverService under the hood with all of the core ArgumentResolvers.

The `ArgumentResolver`s provided in the core implementation are:
- [PayloadArgumentResolver](./java-dynamic-sqs-listener-core/java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver.java): this will look for any arguments with the [@Payload](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java) annotation and try to parse the message body into the type of the argument via a [PayloadMapper](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/mapper/PayloadMapper.java), such as the [JacksonPayloadMapper](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/mapper/JacksonPayloadMapper.java) that uses a Jackson `ObjectMapper` to parse the message body.
- [MessageIdArgumentResolver](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/messageid/MessageIdArgumentResolver.java): this will look for any String arguments with the [@MessageId](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/messageid/MessageId.java) annotation and place the message id into that argument.
- [AcknowledgeArgumentResolver](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/acknowledge/AcknowledgeArgumentResolver.java): this will look for any arguments that are the [Acknowledge](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/acknowledge/Acknowledge.java) type. This can be used to manually acknowledge a message (deletes from the queue) being processed **instead** of it automatically being acknowledged if there was no exception thrown during processing.
- [VisibilityExtenderArgumentResolver](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/visibility/VisibilityExtenderArgumentResolver.java): this will look for any arguments that are the [VisibilityExtender](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/visibility/VisibilityExtender.java) type. This can be used to extend the visibility of the current message and therefore not make it accessible to other consumers. 

### Message Broker
This [MessageBroker](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/broker) is the main container that controls the whole flow of messages from the [MessageRetriever](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever) to the [MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) and do it with as many or as little concurrent threads as it requires.

The [MessageBroker](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/broker)s provided in the core implementation are:
- [SingleThreadedMessageBroker](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/broker/singlethread/SingleThreadedMessageBroker.java): this implementation only runs on a single thread and therefore only a single message can be processed at once. This would most often just be useful for local development and testing and does not have a significant production use case.
- [ConcurrentMessageBroker](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/broker/concurrent/ConcurrentMessageBroker.java): this implementation will run on multiple threads each processing messages. It has dynamic configuration and this allows the rate of concurrency to change dynamically while the application is running.