# Spring - How to add Brave Tracing
If your application is using Brave for tracing the application, it would be good to be able to continue your tracing
for the messages that are being processed. This is done using
a [MessageProcessingDecorators](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java) which will
wrap the processing of the message with custom logic.

## Steps
1. Make sure you have the brave tracing dependency configured, e.g. via Spring Sleuth. See their documentation on how to do this.
1. Add the [brave-message-processing-decorator](../../../extensions/brave-message-processing-decorator) module
    ```xml
    <dependency>
        <groupId>com.jashmore</groupId>
        <artifactId>brave-message-processing-decorator</artifactId>
        <version>${library.version}</version>
    </dependency>
    ```
1. Now any processing message should have tracing information

## Example
See the [spring-sleuth-example](../../../examples/spring-sleuth-example/README.md) for a Spring application that has this setup.

## Including Brave information in your SQS messages
If you want to include the Trace information into your SQS message so that the message listener continues the trace,
you can use the [sqs-brave-tracing](../../../util/sqs-brave-tracing) Utility module to  add the tracing information to the
SQS Message Attributes.

An example of this being done is in the
[spring-sleuth-example](../../../examples/spring-sleuth-example/src/main/java/com/jashmore/sqs/examples/sleuth/Application.java).