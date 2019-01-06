# Java Dynamic SQS Listener
[![Build Status](https://travis-ci.org/JaidenAshmore/java-dynamic-sqs-listener.png)](https://travis-ci.org/JaidenAshmore/java-dynamic-sqs-listener)
[![Coverage Status](https://coveralls.io/repos/github/JaidenAshmore/java-dynamic-sqs-listener/badge.svg?branch=master)](https://coveralls.io/github/JaidenAshmore/java-dynamic-sqs-listener?branch=master)

This SQS Listener implementation that is designed from the ground up to allow for easier configuration and extensibility.  While the other implementations of SQS
listeners were easy to set up and provided most use cases, they don't provide the extensibility and dynamic requirements that are needed by some services.
Therefore, the following characteristics were crucial components in the design of this implementation:

- **Configurability**: The implementation should be built with configurability in mind and should allow for different parts of the framework to be replaced
or extended based on the specific use case.  For example, you want to change how messages are retrieved from the SQS queue and so that part should be able to
be easily replaced without impacting other parts of the framework.
- **Runtime dynamic control**: The implementation should allow for different properties of the framework to change while the application is running. For example,
when processing messages concurrently, the rate of concurrency can be dynamically changed.

## Quick start guide using a Spring Starter
This guide will give a quick guide to getting started for Spring Boot using the spring starter but this framework is not tied to Spring in anyway. Note that
this is pretty much the same steps as the leading SQS Listener implementation and for details about the other functionality see the
[wiki](https://github.com/JaidenAshmore/java-dynamic-sqs-listener/wiki).

Include the maven dependency in your Spring Boot com.jashmore.examples.spring.aws.Application:
```xml
<dependency>
    <groupId>com.jashmore</groupId>
    <artifactId>java-dynamic-sqs-listener-spring-starter</artifactId>
    <version>${sqs.listener.version}</version>
</dependency>
```

In one of your `Configuration` classes, enable the framework using the `@EnableQueueListeners` annotation:

```java
// package and other imports
import com.jashmore.sqs.spring.annotation.EnableQueueListeners;

@EnableQueueListeners
@Configuration
public class MyConfig {

    // Note that you can define a AmazonSQSAsync here for example to point to a locally running queue otherwise a default is used. See
    // QueueListenerConfiguration for the default client used 
}
```

In one of your beans attach a `@QueueListener` annotations to a method that can process messages.

```java
@Service
public class MyService {
    @QueueListener("http://localhost:4572/q/myQueue") // The queue here can point to your SQS server, e.g. a local SQS server 
    public void messageListener(@Payload final String payload) {
        // process the message payload here
    }
}
```

When you run the application, any messages that arrive on the queue will be passed into this method.

## Testing locally with an example
The easiest way to see the framework working is to run one of the examples locally. These all use an in memory [ElasticMQ](https://github.com/adamw/elasticmq)
SQS Server so you don't need to do any setting up of queues to get it working. For example to run a sample Spring com.jashmore.examples.spring.aws.Application you can use the
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

## Full Documentation
To keep the README minimal and easy to digest the rest of the documentation is kept in the [doc](./doc/documentation.md) folder.

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
 
