# Java Dynamic SQS Listener
![Test](https://github.com/JaidenAshmore/functional-futures/workflows/Build%20and%20Test/badge.svg)
[![Coverage Status](https://coveralls.io/repos/github/JaidenAshmore/java-dynamic-sqs-listener/badge.svg?branch=3.x)](https://coveralls.io/github/JaidenAshmore/java-dynamic-sqs-listener?branch=3.x)
[![Maven Central](https://img.shields.io/maven-central/v/com.jashmore/java-dynamic-sqs-listener-parent.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.jashmore%22%20AND%20%22java-dynamic-sqs-listener%22)

The Java Dynamic SQS Listener is a library that simplifies the listening of messages on an [AWS SQS queue](https://aws.amazon.com/sqs/).  It has been
built from the ground up with the goal of making it easily customisable, allowing each component of the library to be easily interchanged if desired, as well
as allowing dynamic changes to configuration during runtime, for example the amount of messages being processed concurrently can be changed via a feature flag
or other configuration properties.

To keep the README minimal and easy to digest, the rest of the documentation is kept in the [doc](./doc/documentation.md) folder which provides a more
thorough overview of how to use the library.

## Spring Quick Guide
The following provides some examples using the Spring Starter for this library. *Note that this library is not Spring specific as the main implementations are
kept in the [core module](./java-dynamic-sqs-listener-core) which is framework agnostic.*

### Using the Spring Starter
This guide will give a quick guide to getting started for Spring Boot using the Spring Stater.

Include the maven dependency in your Spring Boot pom.xml:
```xml
<dependency>
    <groupId>com.jashmore</groupId>
    <artifactId>java-dynamic-sqs-listener-spring-starter</artifactId>
    <version>${sqs.listener.version}</version>
</dependency>
```

In one of your beans, attach a
[@QueueListener](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-core/src/main/java/com/jashmore/sqs/spring/container/basic/QueueListener.java)
annotation to a method indicating that it should process messages from a queue.

```java
@Service
public class MyMessageListener {
    @QueueListener("${insert.queue.url.here}") // The queue here can point to your SQS server, e.g. a local SQS server or one on AWS 
    public void processMessage(@Payload final String payload) {
        // process the message payload here
    }
}
```

This will use any user configured `SqsAsyncClient` in the application context for connecting to the queue, otherwise if none is defined, a default
is provided that will look for AWS credentials/region from multiple areas, like the environment variables. See
[How to connect to AWS SQS Queues](./doc/how-to-guides/how-to-connect-to-aws-sqs-queue.md) for information about connecting to an actual queue in SQS.

## Core Infrastructure
This library has been divided into four main components each with distinct responsibilities. The following is a diagram describing a simple flow of a
single SQS message flowing through each of the components to eventually be executed by some code.

![Core Framework Architecture Diagram](./doc/resources/architecture_diagram.png "Core Framework Architecture Diagram")

Details about each component is:
- The [MessageRetriever](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) handles
obtaining messages from the SQS queue. This can optimise the retrieval of messages by batching requests for messages or prefetching messages before
they are needed.
- The [MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) controls
the processing of a message from the queue by delegating it to the corresponding Java method that handles the message.
- The [ArgumentResolverService](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) is used by the 
[MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) to populate the
arguments of the method being executed from the message. For example, a parameter with the
[@Payload](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java) annotation will be resolved with the
body of the message cast to that type (e.g. a POJO).
- The [MessageBroker](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/broker/MessageBroker.java) is the main container that controls the whole flow
of messages from the [MessageRetriever](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) to the
[MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java). It can determine when more messages
are to be processed and the rate of concurrency for processing messages.
- The [MessageResolver](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/resolver/MessageResolver.java) is used when the message has been
successfully processed and it is needed to be removed from the SQS queue so it isn't processed again.

See the [Core Implementations Overview](./doc/core-implementations-overview.md) for more information about the core implementations provided by this library.

### Dependencies
The framework relies on the following dependencies and therefore it is recommended to upgrade the applications dependencies to a point somewhere near these
for compatibility.
- [Core Framework](java-dynamic-sqs-listener-core)
  - JDK 1.8 or higher
  - [AWS SQS SDK](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/welcome.html): 2.13.7
  - [Guava](https://github.com/google/guava): 29.0-jre
  - [Jackson Databind](https://github.com/FasterXML/jackson-databind): 2.11.0
- [Spring Framework](java-dynamic-sqs-listener-spring)
  - All of the core dependencies
  - [Spring Boot](https://github.com/spring-projects/spring-boot): 2.3.1.RELEASE

### How to Mark the message as successfully processed
When the method executing the message finishes without throwing an exception, the
[MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) will acknowledge the message
as a success, therefore removing it from the queue. If any exception is thrown, the message will not be acknowledged and if there is a redrive policy the
message may be placed back onto the queue for another attempt.

Note that if the method contains an
[Acknowledge](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/argument/Acknowledge.java) argument it is now up to the method
to manually acknowledge the message as a success as the [MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java)
will not acknowledge the message automatically when the method executes without throwing an exception.

### Setting up a queue listener that batches requests for messages
The [Spring Cloud AWS Messaging](https://github.com/spring-cloud/spring-cloud-aws/tree/master/spring-cloud-aws-messaging) `@SqsListener` works by requesting
a set of messages from the SQS and when they are done it will request some more. There is one disadvantage with this approach in that if 9/10 of the messages
finish in 10 milliseconds but one takes 10 seconds no other messages will be picked up until that last message is complete. The
[@QueueListener](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-core/src/main/java/com/jashmore/sqs/spring/container/basic/QueueListener.java)
provides the same basic functionality but it also provides a timeout where eventually it will request for more messages even for the threads that are
ready for another message. It will also batch the removal of messages from the queue and therefore with a concurrency level of 10, if there are a lot messages
on the queue, only 2 requests would be made to SQS for retrieval and deletion of messages. The usage is something like this:

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
[@PrefetchingQueueListener](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-core/src/main/java/com/jashmore/sqs/spring/container/prefetch/PrefetchingQueueListener.java)
annotation can be used to pretech messages in a background thread while messages are currently being processed.  The usage is something like this:

```java
@Service
public class MyMessageListener {
    @PrefetchingQueueListener(value = "${insert.queue.url.here}", concurrencyLevel = 10, desiredMinPrefetchedMessages = 5, maxPrefetchedMessages = 10) 
    public void processMessage(@Payload final String payload) {
        // process the message payload here
    }
}
```

In this example, if the amount of prefetched messages is below the desired amount of prefetched messages it will try and get as many messages as possible
maximum.

*Note: because of the limit of the number of messages that can be obtained from SQS at once (10), having the maxPrefetchedMessages more than
10 above the desiredMinPrefetchedMessages will not provide much value as once it has prefetched more than the desired prefeteched messages it will
not prefetch anymore.*

### Adding a custom argument resolver
There are some core [ArgumentResolvers](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) provided in the
application but if they don't provide the ease required for the application you can define your own. As an example, the following is how we can resolve an
argument for the method where the payload of the message has been converted to uppercase. 

1. We will use an annotation on the field to indicate how the message should be resolved.
    ```java
    @Retention(value = RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface UppercasePayload {
    }
    ```
1. Implement the [ArgumentResolver](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) interface where it will
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
    You may be curious why a custom `AnnotationUtils.findParameterAnnotation` function is being used instead of getting the annotation directly from the parameter.
    The reason for this is due to potential proxying of beans in the application, such as by applying Aspects around your code via CGLIB.  As libraries, like
    CGLIB, won't copy the annotations to the proxied classes the resolver needs to look through the class hierarchy to find the original class to get the
    annotations. For more information about this, take a look at the JavaDoc provided in
    [AnnotationUtils](./util/common-utils/src/main/java/com/jashmore/sqs/util/annotation/AnnotationUtils.java). You can also see an example of
    this problem being tested in
    [PayloadArgumentResolver_ProxyClassTest.java](./java-dynamic-sqs-listener-core/src/test/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver_ProxyClassTest.java).
1. Include the custom [ArgumentResolver](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) in the application
context for automatic injection into the
[ArgumentResolverService](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java).
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
    @QueueListener("${insert.queue.url.here}") // The queue here can point to your SQS server, e.g. a local SQS server or one on AWS 
    public void processMessage(@UppercasePayload final String uppercasePayload) {
        // process the message payload here
    }
    ```
    
For a more extensive guide for doing this, take a look at
[Spring - How to add a custom Argument Resolver](doc/how-to-guides/spring/spring-how-to-add-custom-argument-resolver.md).

### Building a custom queue listener annotation
The core Queue Listener annotations may not provide the exact use case necessary for the application and they also do not provide any dynamic functionality and
therefore it would be useful to provide your own annotation. See
[Spring - How to add a custom queue wrapper](doc/how-to-guides/spring/spring-how-to-add-own-queue-listener.md) for a guide to doing this.

### Testing locally an example Spring Boot app with the Spring Starter 
The easiest way to see the framework working is to run one of the examples locally. These all use an in memory [ElasticMQ](https://github.com/adamw/elasticmq)
SQS Server so you don't need to do any setting up of queues on AWS to test this yourself. For example to run a sample Spring Application you can use the
[Spring Starter Example](examples/spring-starter-examples/src/main/java/com/jashmore/sqs/examples).

1. Build the framework
```bash
mvn clean install -DskipTests
```

1. Run the Spring Starer Example Spring Boot app
```bash
(cd examples/spring-starter-examples && mvn spring-boot:run)
``` 

### Testing locally a dynamic concurrency example
This shows an example of running the SQS Listener in a Java application that will dynamically change the concurrency
level while it is executing.

This examples works by having a thread constantly placing new messages while the SQS Listener will randomly change
the rate of concurrency every 10 seconds.

1. Build the framework
```bash
mvn clean install -DskipTests
```

1. Run the Spring Starer Example Spring Boot app
```bash
(cd examples/core-examples && mvn exec:java)
``` 

### Connecting to multiple AWS Accounts using the Spring Starter
If the Spring Boot application needs to connect to SQS queues across multiple AWS Accounts, you will need to provide a
[SqsAsyncClientProvider](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/client/SqsAsyncClientProvider.java)
which will be able to obtain a specific `SqsAsyncClient` based on an identifier. For more information on how to do this, take a look at the documentation
at [How To Connect to Multiple AWS Accounts](doc/how-to-guides/spring/spring-how-to-connect-to-multiple-aws-accounts.md)

### Versioning Message Payloads using Apache Avro Schemas
As the application grows, it may be beneficial to allow for versioning of the schema so that the consumer can still serialize messages from producers sending
different versions of the schema. To allow for this the [spring-cloud-schema-registry-extension](extensions/spring-cloud-schema-registry-extension) was written
to support this functionality. See the [README.md](extensions/spring-cloud-schema-registry-extension/README.md) for this extension for more details.

### Comparing Libraries
If you want to see the difference between this library and others like the
[Spring Cloud AWS Messaging](https://github.com/spring-cloud/spring-cloud-aws/tree/master/spring-cloud-aws-messaging) and
[Amazon SQS Java Messaging Library](https://github.com/awslabs/amazon-sqs-java-messaging-lib), take a look at the [sqs-listener-library-comparison](./examples/sqs-listener-library-comparison)
module. This allows you to test the performance and usage of each library for different scenarios, such as heavy IO message processing, etc.

### Other examples
See [examples](./examples) for all the other available examples. 

## Bugs and Feedback
For bugs, questions and discussions please use [Github Issues](https://github.com/JaidenAshmore/java-dynamic-sqs-listener/issues).

## Contributing
See [CONTRIBUTING.md](./CONTRIBUTING.md) for more details.

## License

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
