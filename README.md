[![Build Status](https://travis-ci.org/JaidenAshmore/java-dynamic-sqs-listener.png)](https://travis-ci.org/JaidenAshmore/java-dynamic-sqs-listener)
[![Coverage Status](https://coveralls.io/repos/github/JaidenAshmore/java-dynamic-sqs-listener/badge.svg?branch=master)](https://coveralls.io/github/JaidenAshmore/java-dynamic-sqs-listener?branch=master)

# Java Dynamic SQS Listener - SQS Listener with easy configuration and extensibility
SQS Listener implementation that is designed from the ground up to allow for easier configuration and extensibility.

## Why Dynamic SQS Listener?
Whilst the other implementations of SQS listeners were easy to set up and provided most use cases, they don't provide the extensibility and dynamic
requirements that are needed by some services. Therefore, the following characteristics
were crucial components in the design of this implementation:

- **Configurability**: The Dynamic SQS Listener is built with configurability and extensibility in mind allowing for different parts
of the listener to be replaced or extended based on the specific use case.  For example, you may find that the specific
implementation for how messages are consumed from the SQS queue doesn't work as intended and so can be replaced without
needing to make changes to the other parts of the framework.
- **Dynamic control**: The other major use case for this implementation is allowing for the configuration of the listener while it is running. For example,
your service is rolling out a new feature and wants to place the rate of message listening behind a feature flag. With the use of a
`ConcurrentMessageBroker` the service is able to dynamically throttle the number of threads processing messages via a
simple properties supplier.

## Usage

### API
This modules contains the API for the framework and includes a lot of documentation about how each component should be
interacting with each other.  This contains the interfaces that will be implemented by the core implementation and 
should be used when building a custom part of the framework.

### Core
This provides basic Java implementations of the API which should cover multiple use cases. There is no dependencies
on any external dependencies to promote better integration by reducing dependency hell. Because of this setting up all
the components needed for the framework can take a bit more boilerplate code for the consumer but the advantage is
the improved ease of integration.

### Spring Boot Starter
The Spring Boot Starter used to reduce the amount of boilerplate code by providing auto configuration and annotations for hooking
into the framework. This still needs to be implemented.

### Other implementations?
Pull Requests are welcome for adding any other implementations for frameworks or types of listeners. For example, you want to
try to use RxJava for your `MessageBroker` implementation you could add this in the core package for others to use.

## Architecture
As can be seen in [java-dynamic-sqs-listener-api](./java-dynamic-sqs-listener-api) the listener has been divided into
four main responsibilities. Each of these have been built with no reliance on each other and therefore the consumer
can use or interchange the implementations of any of these with ease.

### [Message Retriever](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever)
The message retriever is one of the most important parts of the architecture and it has the responsibility of figuring
out how to obtain new messages from the queue. The [MessageRetriever](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java)
and [AsyncMessageRetriever](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/AsyncMessageRetriever.java)
API has been kept to a bare minimum and so the complexity for how it wants to handle obtaining the messages is kept
internal to the implementation. Note that the `MessageRetriever` must be thread safe as there could be multiple threads
all requesting messages to process.

### [Message Processor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor)
The [MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java)
is the part that handles how it should process the message that has been obtained from the SQS queue. It will take the
message and run it through a Java method that will handle the message. This has the responsibility of resolving any
arguments for the method and how the message should be marked as successfully processed, e.g. delete the message from
SQS.

### [Argument Resolvers](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument)
The message processor above needs a way to know how arguments in the method should be populated, for example, if the
method wants to know the message id for this message how does this get populated. This is the responsibility of the
[ArgumentResolverService](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)
which will be able to look at the method parameters and resolve the arguments from this message being processed.  To
increase testability and extensibility the [ArgumentResolver](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
is used to handle each different type of argument, for example the
[PayloadArgumentResolver](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver.java)
which populates arguments annotated with [@Payload](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java)
by parsing the message body into that argument.

### [Message Broker](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/broker)
This is the main container that controls the whole flow of messages from SQS to the specific method to process this. It
is basically the high level broker that glues all of the above components together. For example, the
[ConcurrentMessageBroker](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/broker/concurrent/ConcurrentMessageBroker.java)
will spin up multiple threads that will retrieve messages and pass them to the message processor for processing.
