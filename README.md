# Java Dynamic SQS Listener
[![Build Status](https://travis-ci.org/JaidenAshmore/java-dynamic-sqs-listener.png)](https://travis-ci.org/JaidenAshmore/java-dynamic-sqs-listener)
[![Coverage Status](https://coveralls.io/repos/github/JaidenAshmore/java-dynamic-sqs-listener/badge.svg?branch=master)](https://coveralls.io/github/JaidenAshmore/java-dynamic-sqs-listener?branch=master)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/JaidenAshmore/java-dynamic-sqs-listener.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/JaidenAshmore/java-dynamic-sqs-listener/alerts/)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/JaidenAshmore/java-dynamic-sqs-listener.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/JaidenAshmore/java-dynamic-sqs-listener/context:java)

This SQS Listener implementation that is designed from the ground up to allow for easier configuration and extensibility.  While the other implementations of SQS
listeners were easy to set up and provided most use cases, they don't provide the extensibility and dynamic requirements that are needed by some services.
Therefore, the following characteristics were crucial components in the design of this implementation:

- **Configurability**: The implementation should be built with configurability in mind and should allow for different parts of the framework to be replaced
or extended based on the specific use case.  For example, you want to change how messages are retrieved from the SQS queue and so that part should be able to
be easily replaced without impacting other parts of the framework.
- **Runtime dynamic control**: The implementation should allow for different properties of the framework to change while the application is running. For example,
when processing messages concurrently, the rate of concurrency can be dynamically changed.

## Full Documentation
To keep the README minimal and easy to digest the rest of the documentation is kept in the [doc](./doc/documentation.md) folder.

## Core Infrastructure
This library has been divided into four main areas and are all interchangeable. If any of the components don't work for you, you can replace it with your
own. The following is the diagram for how a single message would be processed through the library.

![Core Framework Architecture Diagram](./doc/resources/architecture_diagram.png "Core Framework Architecture Diagram")

- The [MessageRetriever](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) is the component that handles
obtaining messages from the SQS queue. This allows for the ability to optimise the retrieval of messages, such as by batching requests for
retrieving messages or prefetching messages before they are needed.
- The [MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) is the component that controls
the processing of a message from the queue by delegating it to the corresponding Java method that handles the message.
- The [ArgumentResolverService](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) is used by the 
[MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) to populate the
arguments of the method being executed from the message. For example, a parameter with the
[@Payload](./java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/Payload.java) annotation will be resolved with the
body of the message cast to that type (e.g. a POJO).
- The [MessageBroker](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/broker) is the main container that controls the whole flow
of messages from the [MessageRetriever](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) to the
[MessageProcessor](./java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java). It can determine when more messages
are to be processed and the rate of concurrency for processing messages.

See [Core Implementations Overview](./doc/core-implementations-overview.md) for more information about the core implementations provided by this library.

## Spring Quick Guide
The following provides some examples using the Spring Framework. Note that this is not a Spring specific library and the main components of the codebase
are maintained in the [core module](./java-dynamic-sqs-listener-core) with the [spring module](./java-dynamic-sqs-listener-spring) being a consumer. 

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
[@QueueListener](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/container/basic/QueueListener.java)
annotation to a method indicating that it should process messages from a queue.

```java
@Service
public class MyService {
    @QueueListener("${insert.queue.url.here}") // The queue here can point to your SQS server, e.g. a local SQS server or one on AWS 
    public void messageListener(@Payload final String payload) {
        // process the message payload here
    }
}
```

This will use any user configured `SqsAsyncClient` in the application context for connecting to the queue, otherwise if none is defined, a default
is provided that will look for AWS credentials/region from multiple areas, like the environment variables. See
[How to connect to AWS SQS Queues](./doc/how-to-guides/how-to-connect-to-aws-sqs-queue.md) for information about connecting to an actual queue.

### Setting up a queue listener that batches requests for messages
The [Spring Cloud AWS Messaging](https://github.com/spring-cloud/spring-cloud-aws/tree/master/spring-cloud-aws-messaging) `@SqsListener` works by requesting
a set of messages from the SQS and when they are done it will request some more. There is one disadvantage with this approach in that if 9/10 of the messages
finish in 10 milliseconds but one takes 10 seconds no other messages will be picked up until that last message is complete. The
[@QueueListener](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/container/basic/QueueListener.java)
provides the same basic functionality but it also provides a timeout where if there are threads waiting for messages they can retrieve them. The usage is
something like this:

```java
@Service
public class MyService {
    @QueueListener(value = "${insert.queue.url.here}", concurrencyLevel = 10, maxPeriodBetweenBatchesInMs = 2000) 
    public void messageListener(@Payload final String payload) {
        // process the message payload here
    }
}
```

In this example above we have set it to process 10 messages at once and when there are threads wanting more messages it will wait for a maximum of this
batch period before requesting messages or until all 10 threads are wanting messages before calling out to the SQS queue.

### Setting up a queue listener that prefetches messages
The amount of messages for a service may be extremely high that prefetching messages may be a way to optimise the throughput of the application. The
[@PrefetchingQueueListener](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/container/prefetch/PrefetchingQueueListener.java)
annotation can be used to set messages to be prefetched in a background thread.

```java
@Service
public class MyService {
    @PrefetchingQueueListener(value = "${insert.queue.url.here}", concurrencyLevel = 10, desiredMinPrefetchedMessages = 5, maxPrefetchedMessages = 10) 
    public void messageListener(@Payload final String payload) {
        // process the message payload here
    }
}
```

In this example, if the amount of prefetched messages is below the desired amount of prefetched messages it will try and get as many messages as possible
maximum. Note: because of the limit of the number of messages that can be obtained from SQS at once (10), having the maxPrefetchedMessages more than
10 above the desiredMinPrefetchedMessages will not provide much value as it will not try and request 20 messages for example.

### Using custom library components in queue listener annotation
The [CustomQueueListener](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/container/custom/CustomQueueListener.java)
is provided to allow for consumers to create queue listeners with their own implementations of the library components via factory beans. The steps to do
this include:

1. Define factories that can construct all of the components of the library needed for this queue listener. The easiest way is to use lambdas but actual
implementations of the factory is appropriate too. See that each factory will build the main components of the library.
    ```java
    @Configuration
    public class MyConfig {
           @Bean
           public MessageRetrieverFactory myMessageRetrieverFactory(final SqsAsyncClient sqsAsyncClient) {
               return (queueProperties) -> new MyCustomMessageRetriever(queueProperties);
           }

           @Bean
           public MessageProcessorFactory myMessageProcessorFactory(final ArgumentResolverService argumentResolverService,
                                                                    final SqsAsyncClient sqsAsyncClient) {
                // In this scenario we return a core implementation
                return (queueProperties, bean, method) -> new DefaultMessageProcessor(argumentResolverService, queueProperties, sqsAsyncClient, method, bean);    
           }
        
           @Bean
           public MessageBrokerFactory myMessageBrokerFactory() {
                return (messageRetriever, messageProcessor) -> new MyMessageBroker(messageRetriever, messageProcessor);   
           }
    }
    ```
1. Wrap a method with the
[CustomQueueListener](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/container/custom/CustomQueueListener.java)
containing the bean names of the factories that you made above, e.g. `myMessageRetrieverFactory`.
    ```java
    @Service
    public class MyService {
        @CustomQueueListener(value = "${insert.queue.url.here}",  
               messageBrokerFactoryBeanName = "myMessageBrokerFactory",
               messageProcessorFactoryBeanName = "myMessageProcessorFactory",
               messageRetrieverFactoryBeanName = "myMessageRetrieverFactory"
        ) 
        public void messageListener(@Payload final String payload) {
            // process the message payload here
        }
    }
    ```
    
### Building a new queue listener annotation
The [CustomQueueListener](./java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/container/custom/CustomQueueListener.java)
is a bit verbose so it would most likely be more useful to provide your own annotation that will build the components needed for this use case. See
[Spring - How to add a custom queue wrapper](doc/how-to-guides/spring/spring-how-to-add-custom-queue-wrapper.md) for a guide to doing this.

### Testing locally with an example
The easiest way to see the framework working is to run one of the examples locally. These all use an in memory [ElasticMQ](https://github.com/adamw/elasticmq)
SQS Server so you don't need to do any setting up of queues to get it working. For example to run a sample Spring
`com.jashmore.examples.spring.aws.Application` you can use the
[Spring Starter Example](examples/java-dynamic-sqs-listener-spring-starter-examples/src/main/java/com/jashmore/sqs/examples).

1. Clone this repository
```bash
git clone git@github.com:JaidenAshmore/java-dynamic-sqs-listener.git  
```

1. Build the framework
```bash
mvn package -DskipTests
```

1. Change directory to the example
```bash
cd examples/java-dynamic-sqs-listener-spring-starter-examples
```

1. Run the Spring Boot app
```bash
mvn spring-boot:run
``` 

See [examples](./examples) for all of the available examples, 

## Bugs and Feedback
For bugs, questions and discussions please use the [Github Issues](https://github.com/JaidenAshmore/java-dynamic-sqs-listener/issues).

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
 
