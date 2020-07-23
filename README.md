# Java Dynamic SQS Listener

[![Build and Test](https://github.com/JaidenAshmore/java-dynamic-sqs-listener/workflows/Build%20and%20Test/badge.svg?branch=4.x)](https://github.com/JaidenAshmore/java-dynamic-sqs-listener/actions?query=workflow%3A%22Build+and+Test%22+branch%3A4.x)
[![Coverage Status](https://coveralls.io/repos/github/JaidenAshmore/java-dynamic-sqs-listener/badge.svg?branch=4.x)](https://coveralls.io/github/JaidenAshmore/java-dynamic-sqs-listener?branch=4.x)
[![Maven Central](https://img.shields.io/maven-central/v/com.jashmore/java-dynamic-sqs-listener-api?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.jashmore%22%20AND%20%22java-dynamic-sqs-listener%22)

The Java Dynamic SQS Listener is a library that simplifies the listening of messages on an [AWS SQS queue](https://aws.amazon.com/sqs/).  It has been
built from the ground up with the goal of making it easily customisable, allowing each component of the library to be interchanged if desired. The listeners
also allow dynamic changes to the configuration during runtime, for example it could be built to allow the amount of concurrent processing messages to be
controlled by a feature flag.

## Spring Boot Quick Guide

The following provides some examples using the Spring Boot Starter for this library. *Note that the [core](./core) implementation is framework agnostic and
is not reliant on using Spring.*

### Using the Spring Boot Starter

This guide will give a quick guide to getting started for Spring Boot using the Spring Stater.

#### Maven

```xml
<dependency>
    <groupId>com.jashmore</groupId>
    <artifactId>java-dynamic-sqs-listener-spring-starter</artifactId>
    <version>${sqs.listener.version}</version>
</dependency>
```

#### Gradle

```kotlin
dependencies {
    implementation("com.jashmore:java-dynamic-sqs-listener-spring-starter:${sqs.listener.version}")
}
```

In one of your beans, attach a
[@QueueListener](./spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/basic/QueueListener.java)
annotation to a method indicating that it should process messages from a queue.

```java
@Service
public class MyMessageListener {
    // The queue here can point to your SQS server, e.g. a
    // local SQS server or one on AWS
    @QueueListener("${insert.queue.url.here}")
    public void processMessage(@Payload final String payload) {
        // process the message payload here
    }
}
```

This will use any configured `SqsAsyncClient` in the application context for connecting to the queue, otherwise a default
will be provided that will look for AWS credentials/region from multiple areas, like the environment variables.

See [How to connect to AWS SQS Queues](./doc/how-to-guides/how-to-connect-to-aws-sqs-queue.md) for information about connecting to an actual queue in SQS.

## Core Infrastructure

This library has been divided into isolated components each with distinct responsibilities. The following is a diagram describing a simple flow of a
single SQS message flowing through each of the components to eventually be executed by some code.

![Core Framework Architecture Diagram](./doc/resources/architecture_diagram.png "Core Framework Architecture Diagram")

Details about each component is:

- The [MessageRetriever](./api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) handles
obtaining messages from the SQS queue. This can optimise the retrieval of messages by batching requests for messages or prefetching messages before
they are needed.
- The [MessageProcessor](./api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) controls
the processing of a message from the queue by delegating it to the corresponding Java method that handles the message.
- The [ArgumentResolverService](./api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) is used by the
[MessageProcessor](./api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) to populate the
arguments of the message listener method. For example, a parameter with the
[@Payload](./core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java) annotation will be resolved with the
body of the message cast to that type (e.g. a POJO).
- The [MessageBroker](./api/src/main/java/com/jashmore/sqs/broker/MessageBroker.java) is the main container that controls the whole flow
of messages from the [MessageRetriever](./api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) to the
[MessageProcessor](./api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java). It can determine when more messages
are to be processed and the rate of concurrency for processing messages.
- The [MessageResolver](./api/src/main/java/com/jashmore/sqs/resolver/MessageResolver.java) is used after successful processing of the message and its
responsibility is to remove the message from the SQS queue, so it is not processed again if there is a re-drive policy.

For more information about the core implementations provided by this library, see the [Core Implementations Overview](./doc/core-implementations-overview.md).

## Dependencies

The framework relies on the following dependencies and therefore it is recommended to upgrade the applications dependencies to a point somewhere near these
for compatibility.

- [Core Framework](./core)
  - JDK 1.8 or higher
  - [AWS SQS SDK](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/welcome.html)
  - [Jackson Databind](https://github.com/FasterXML/jackson-databind)
  - [SLF4J API](https://github.com/qos-ch/slf4j)
- [Spring Framework](./spring)
  - All the core dependencies above
  - [Spring Boot](https://github.com/spring-projects/spring-boot)
  
See the [build.gradle.kts](build.gradle.kts) for the specific versions of these dependencies.

## How to Guides

1. [How to Connect to an AWS SQS Queue](doc/how-to-guides/how-to-connect-to-aws-sqs-queue.md): necessary for actually using this framework in live environments
1. Core Framework How To Guides
    1. [How to implement a custom ArgumentResolver](doc/how-to-guides/core/core-how-to-implement-a-custom-argument-resolver.md): useful for changing resolution
    of arguments in the message listener
    1. [How to manually acknowledge message](doc/how-to-guides/core/core-how-to-mark-message-as-successfully-processed.md): useful for when you want to mark the
    message as successfully processed before the method has finished executing
    1. [How to add Brave Tracing](doc/how-to-guides/core/core-how-to-add-brave-tracing.md): for including Brave Tracing information to your messages
    1. [How to implement a custom MessageRetriever](doc/how-to-guides/core/core-how-to-implement-a-custom-message-retrieval.md): useful for changing the logic
    for obtaining messages from the SQS queue if the core implementations do not provided the required functionality
    1. [How to extend a message's visibility during processing](doc/how-to-guides/core/core-how-to-extend-message-visibility-during-processing.md): useful for
    extending the visibility of a message in the case of long processing so it does not get put back on the queue while processing
    1. [How to create a MessageProcessingDecorator](doc/how-to-guides/core/core-how-to-create-a-message-processing-decorator.md): guide for writing your own
    decorator to wrap a message listener's processing of a message
    1. [How to use the Core Kotlin DSL](doc/how-to-guides/core/core-how-to-use-kotlin-dsl.md): guide for using the core library easier using a Kotlin
    DSL for constructing message listeners
1. Spring How To Guides
    1. [How to add a custom ArgumentResolver to a Spring application](doc/how-to-guides/spring/spring-how-to-add-custom-argument-resolver.md): useful for
    integrating custom argument resolution code to be included in a Spring Application. See [How to implement a custom ArgumentResolver](doc/how-to-guides/core/core-how-to-implement-a-custom-argument-resolver.md)
    for how build a new ArgumentResolver from scratch.
    1. [How to add Brave Tracing](doc/how-to-guides/spring/spring-how-to-add-brave-tracing.md): for including Brave Tracing information to your messages
    1. [How to add custom MessageProcessingDecorators](doc/how-to-guides/spring/spring-how-to-add-custom-message-processing-decorators.md): guide on how
    to autowire custom `MessageProcessingDecorators` into your Spring Queue Listeners.
    1. [How to customise argument resolution](doc/how-to-guides/spring/spring-how-to-customise-argument-resolution.md): guide for overriding the entire
    argument resolution logic
    1. [How to add your own queue listener](doc/how-to-guides/spring/spring-how-to-add-own-queue-listener.md): useful for defining your own annotation for the
    queue listening without the verbosity of a custom queue listener
    1. [How to write Spring Integration Tests](doc/how-to-guides/spring/spring-how-to-write-integration-tests.md): you actually want to test what you are
    writing right?
    1. [How to Start/Stop Queue Listeners](doc/how-to-guides/spring/spring-how-to-start-stop-message-listener-containers.md): guide for starting and stopping the
    processing of messages for specific queue listeners
    1. [How to connect to multiple AWS Accounts](doc/how-to-guides/spring/spring-how-to-connect-to-multiple-aws-accounts.md): guide for listening to queues
    across multiple AWS Accounts
    1. [How to version message payload schemas](doc/how-to-guides/spring/spring-how-to-version-payload-schemas-using-spring-cloud-schema-registry.md): guide
    for versioning payloads using Avro and the Spring Cloud Schema Registry.

## Common Use Cases/Explanations

### How to de-serialise a JSON Payload

The core library by default uses [Jackson](https://github.com/FasterXML/jackson-databind) to de-serialise the message payload and therefore you can
use any Jackson compatible POJO class.

```java
@Service
public class MyMessageListener {
    @QueueListener(value = "${insert.queue.url.here}")
    public void processMessage(@Payload final MyPojo payload) {
        // process the message payload here
    }

    public static class MyPojo {
        private String name;

        public MyPojo() {
            this.name = null;
        }

        public MyPojo(String name) {
            this.name = null;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
```

### Adding a custom argument resolver

There are some core [ArgumentResolvers](./api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) provided in the
application but custom ones can be defined if they don't cover your use case. As an example, the following is how we can populate the message listener
argument with the payload in uppercase.

1. We will use an annotation on the field to indicate how the message should be resolved.

    ```java
    @Retention(value = RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface UppercasePayload {
    }
    ```

1. Implement the [ArgumentResolver](./api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) interface where it will
do the logic for converting the message payload to uppercase.

    ```java
        public class UppercasePayloadArgumentResolver implements ArgumentResolver<String> {
            @Override
            public boolean canResolveParameter(MethodParameter methodParameter) {
                return methodParameter.getParameter().getType().isAssignableFrom(String.class)
                    && AnnotationUtils.findParameterAnnotation(methodParameter, UppercasePayload.class).isPresent();
           }

           @Override
           public String resolveArgumentForParameter(QueueProperties queueProperties, Parameter parameter, Message message) throws ArgumentResolutionException {
               return message.body().toUppercase();
           }
        }
    ```

    You may be curious why we use a custom `AnnotationUtils.findParameterAnnotation` function instead of getting the annotation directly from the parameter.
    The reason for this is due to potential proxying of beans in the application, such as by applying Aspects around your code via CGLIB.  As libraries, like
    CGLIB, won't copy the annotations to the proxied classes the resolver needs to look through the class hierarchy to find the original class to get the
    annotations. For more information about this, take a look at the JavaDoc provided in
    [AnnotationUtils](./util/annotation-utils/src/main/java/com/jashmore/sqs/util/annotation/AnnotationUtils.java). You can also see an example of
    testing this problem in
    [PayloadArgumentResolver_ProxyClassTest.java](./core/src/test/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver_ProxyClassTest.java).

    Also, as this library is not Spring specific, the Spring Annotation classes can not be used.
1. Include the custom [ArgumentResolver](./api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) in the application
context for automatic injection into the
[ArgumentResolverService](./api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java).

     ```java
     @Configuration
     public class MyCustomConfiguration {
        @Bean
        public UppercasePayloadArgumentResolver uppercasePayloadArgumentResolver() {
            return new UppercasePayloadArgumentResolver();
        }
     }
     ```

1. Use the new annotation in your message listener

    ```java
    @Component
    public class MyService {
        @QueueListener("${insert.queue.url.here}") // The queue here can point to your SQS server, e.g. a local SQS server or one on AWS
        public void processMessage(@UppercasePayload final String uppercasePayload) {
            // process the message payload here
        }
    }
    ```

For a more extensive guide for doing this, take a look at
[Spring - How to add a custom Argument Resolver](doc/how-to-guides/spring/spring-how-to-add-custom-argument-resolver.md).

### Increasing the concurrency limit

There is no limit to the number of messages that can be processed in the application and therefore you can process as many messages to the limit
of the threads that the application can handle. Therefore, if you are fine spinning up as many threads as concurrent messages, you can increase
the concurrency to as high of a value as you wish.

Using the Spring Boot Starter you could increase the number of concurrent messages to 100 like the following:

```java
@Service
public class MyMessageListener {
    @QueueListener(value = "${insert.queue.url.here}", concurrencyLevel = 100)
    public void processMessage(@Payload final String payload) {
        // some high IO processing here
    }
}
```

### How to Mark the message as successfully processed

When the method executing the message finishes without throwing an exception, the
[MessageProcessor](./api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) will acknowledge the message
as a success, therefore removing it from the queue. When the method throws an exception, the message will not be acknowledged and if there is a re-drive
policy the queue will perform another attempt of processing the message.

```java
@Service
public class MyMessageListener {
    @QueueListener(value = "queue-name")
    public void processMessage(@Payload final String payload) {
        // do nothing; so the message is considered successfully processed
    }
}
```

Note that if the method contains an
[Acknowledge](./api/src/main/java/com/jashmore/sqs/processor/argument/Acknowledge.java) argument it is now up to the method
to manually acknowledge the message as a success. The [MessageProcessor](./api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java)
has handed off control to the message listener and will not acknowledge the message automatically when the method executes without throwing an exception.

```java
@Service
public class MyMessageListener {
    @QueueListener(value = "${insert.queue.url.here}", concurrencyLevel = 10, maxPeriodBetweenBatchesInMs = 2000)
    public void processMessage(@Payload final String payload, final Acknowledge acknowledge) {
        if (someCondition()) {
            CompletableFuture<?> future = acknowledge.acknowledgeSuccessful();
            future.get();
        }
    }
}
```

### Setting up a queue listener that batches requests for messages

The [Spring Cloud AWS Messaging](https://github.com/spring-cloud/spring-cloud-aws/tree/master/spring-cloud-aws-messaging) `@SqsListener` works by requesting
a set of messages from the SQS and when they are done it will request some more. There is one disadvantage with this approach in that if 9/10 of the messages
finish in 10 milliseconds but one takes 10 seconds no other messages will be picked up until that last message is complete. The
[@QueueListener](./spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/basic/QueueListener.java)
provides the same basic functionality, but it also provides a timeout where eventually it will request for more messages even for the threads that are
ready for another message.

It will also batch the removal of messages from the queue and therefore with a concurrency level of 10, if there are a lot of
messages on the queue, only 2 requests would be made to SQS for retrieval and deletion of messages. The usage is something like this:

```java
@Service
public class MyMessageListener {
    @QueueListener(value = "${insert.queue.url.here}", concurrencyLevel = 10, maxPeriodBetweenBatchesInMs = 2000)
    public void processMessage(@Payload final String payload) {
        // process the message payload here
    }
}
```

In this example above we have set it to process 10 messages at once and when there are threads wanting more messages it will wait for a maximum of 2 seconds
before requesting messages for threads waiting for another message.

### Setting up a queue listener that prefetches messages

When the amount of messages for a service is extremely high, prefetching messages may be a way to optimise the throughput of the application. The
[@PrefetchingQueueListener](./spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/prefetch/PrefetchingQueueListener.java)
annotation can be used to prefetch messages in a background thread while processing the existing messages.  The usage is something like this:

```java
@Service
public class MyMessageListener {
    @PrefetchingQueueListener(value = "${insert.queue.url.here}", concurrencyLevel = 10, desiredMinPrefetchedMessages = 5, maxPrefetchedMessages = 10)
    public void processMessage(@Payload final String payload) {
        // process the message payload here
    }
}
```

In this example, if the amount of prefetched messages is below the desired amount of prefetched messages it will try to get as many messages as possible up
to the maximum specified.

*Note: because of the limit of the number of messages that can be obtained from SQS at once (10), having the maxPrefetchedMessages more than
10 above the desiredMinPrefetchedMessages will not provide much value as once it has prefetched more than the desired prefetched messages it will
not prefetch anymore.*

### Using the Core Library with a Kotlin DSL

If you want a way to more easily build a new
a [MessageListenerContainer](api/src/main/java/com/jashmore/sqs/container/MessageListenerContainer.java) from scratch, you can use the
[Core Kotlin DSL](extensions/core-kotlin-dsl) tool. This can be useful if your Kotlin application does not use Spring, or if you are using Spring and you
want to build your own queue listener annotation.

```kotlin
val container = coreMessageListener("identifier", sqsAsyncClient, queueUrl) {
    retriever = prefetchingMessageRetriever {
        desiredPrefetchedMessages = 10
        maxPrefetchedMessages = 20
    }
    processor = coreProcessor {
        argumentResolverService = coreArgumentResolverService(objectMapper)
        bean = MessageListener()
        method = MessageListener::class.java.getMethod("listen", String::class.java)
    }
    broker = concurrentBroker {
        concurrencyLevel = { 10 }
        concurrencyPollingRate = { Duration.ofSeconds(30) }
    }
    resolver = batchingResolver {
        bufferingSizeLimit = { 5 }
        bufferingTime = { Duration.ofSeconds(2) }
    }
}

container.start()
```

For more details, see the [Core - How to use the Kotlin DSL](doc/how-to-guides/core/core-how-to-use-kotlin-dsl.md) guide.

### Wrapping the Message Listener execution using a MessageProcessingDecorator

If you require to wrap the message listeners with some custom logic, like metrics, logging or other functionality, you can do this using a
[MessageProcessingDecorator](./api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java). This provides callback
functions that will be executed at certain stages of the message processing lifecycle.  For more information on use cases and implementations, take a
look at [Core - How to create a message processing decorator](doc/how-to-guides/core/core-how-to-create-a-message-processing-decorator.md).

### Adding Brave Tracing

If you are using Brave Tracing in your application, for example using Spring Sleuth, you can hook into this system by including the
[brave-extension](extensions/brave-extension) modules.

See
[Core - How to add Brave Tracing](doc/how-to-guides/core/core-how-to-add-brave-tracing.md) and
[Spring - How to add Brave Tracing](doc/how-to-guides/spring/spring-how-to-add-brave-tracing.md) for guides on how to add tracing.

### Building a custom queue listener annotation

The core Queue Listener annotations may not provide the exact use case necessary for the application and therefore it can be useful to provide your
own annotation. See
[Spring - How to add a custom queue wrapper](doc/how-to-guides/spring/spring-how-to-add-own-queue-listener.md) for a guide to doing this.

### Versioning Message Payloads using Apache Avro Schemas

As the application grows, it may be beneficial to allow for versioning of the schema so that the consumer can still serialize messages from producers sending
different versions of the schema. To allow for this the [spring-cloud-schema-registry-extension](extensions/spring-cloud-schema-registry-extension) was written
to support this functionality. See the [README.md](extensions/spring-cloud-schema-registry-extension/README.md) for this extension for more details.

### Connecting to multiple AWS Accounts using the Spring Starter

If the Spring Boot application needs to connect to SQS queues across multiple AWS Accounts, you will need to provide a
[SqsAsyncClientProvider](./spring/spring-api/src/main/java/com/jashmore/sqs/spring/client/SqsAsyncClientProvider.java)
which will be able to obtain a specific `SqsAsyncClient` based on an identifier. For more information on how to do this, take a look at the documentation
at [How To Connect to Multiple AWS Accounts](doc/how-to-guides/spring/spring-how-to-connect-to-multiple-aws-accounts.md).

### Comparing Libraries

If you want to see the difference between this library and others like the
[Spring Cloud AWS Messaging](https://github.com/spring-cloud/spring-cloud-aws/tree/master/spring-cloud-aws-messaging) and
[Amazon SQS Java Messaging Library](https://github.com/awslabs/amazon-sqs-java-messaging-lib), take a look at the [sqs-listener-library-comparison](./examples/sqs-listener-library-comparison)
module. This allows you to test the performance and usage of each library for different scenarios, such as heavy IO message processing, etc.

## Examples

See [examples](./examples) for all the available examples.

### Testing locally an example Spring Boot app with the Spring Starter

The easiest way to see the framework working is to run one of the examples locally. These use an in memory [ElasticMQ](https://github.com/adamw/elasticmq)
SQS Server to simplify getting started. For example, to run a sample Spring Application you can use
the [Spring Starter Example](examples/spring-starter-example/src/main/java/com/jashmore/sqs/examples).

1. Build the framework

```bash
gradle build -x test -x integrationTest
```

1. Run the Spring Starer Example Spring Boot app

```bash
(cd examples/spring-starter-example && gradle bootRun)
```

### Testing locally a dynamic concurrency example

This shows an example of running the SQS Listener in a Java application that will dynamically change the concurrency level while it is executing.

This examples works by having a thread constantly placing new messages while the SQS Listener will randomly change
the rate of concurrency every 10 seconds.

1. Build the framework

```bash
gradle build -x test -x integrationTest
```

1. Run the Spring Starer Example Spring Boot app

```bash
(cd examples/core-example && gradle runApp)
```

## Bugs and Feedback

For bugs, questions and discussions please use [Github Issues](https://github.com/JaidenAshmore/java-dynamic-sqs-listener/issues).

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for more details.

### Setting up Intellij

If you are contributing and want to set up IntelliJ, you can follow this guide: [Setting up IntelliJ](doc/local-development/setting-up-intellij.md).

## License

```text
MIT License

Copyright (c) 2018 JaidenAshmore

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
