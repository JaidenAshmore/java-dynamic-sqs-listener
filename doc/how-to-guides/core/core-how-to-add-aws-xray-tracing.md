# Core - How to add AWS XRay Tracing

_This guide assumes that you have knowledge of Xray and how it works. If not take a look at their tutorials online._

## Steps

1. Add the [AWS Xray SDK V2 Instrumentor](https://github.com/aws/aws-xray-sdk-java/tree/master/aws-xray-recorder-sdk-aws-sdk-v2-instrumentor) dependency which
will automatically send tracing information when you use the SQS Client (as well as other AWS Clients).

    ```xml
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-xray-recorder-sdk-aws-sdk-v2-instrumentor</artifactId>
            <version>2.6.1</version>
        </dependency>
    ```

1. Add the [AWS XRay Core Extension](../../../extensions/aws-xray-extension/core) as a dependency.

    ```xml
        <dependency>
            <groupId>com.jashmore</groupId>
            <artifactId>aws-xray-extension-core</artifactId>
            <version>${dynamic-sqs-listener.version>}</version>
        </dependency>
    ```

1. Wrap the `SqsAsyncClient` in an [XrayWrappedSqsAsyncClient](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/XrayWrappedSqsAsyncClient.java).
This will make sure every call to SQS will make sure the Xray Segment has been started. If this is not done, you will get exceptions thrown in the
library saying that you cannot find the Segment.

    ```java
    SqsAsyncClient client = ...;
    SqsAsyncClient xrayClient = new XrayWrappedSqsAsyncClient(client, AWSXRay.getGlobalRecorder(), new StaticClientSegmentNamingStrategy("service-name"))
    ```

1. Wrap the [MessageProcessor](../../../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) with a
[DecoratingMessageProcessor](../../../core/src/main/java/com/jashmore/sqs/processor/DecoratingMessageProcessor.java) with an instance of the
[BasicXrayMessageProcessingDecorator](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/decorator/BasicXrayMessageProcessingDecorator.java).
This will extract the tracing header from the SQS message and begin a Xray Segment.

    ```java
    MessageProcessor processor = ...;
    List<MessageProcessingDecorator> decorators = new ArrayList<>();
    decorators.add(new BasicXrayMessageProcessingDecorator(new StaticDecoratorSegmentNamingStrategy("service-name")));
    // add any other decorators
    DecoratingMessageProcessor decoratingMessageProcessor = new DecoratingMessageProcessor(
       "identifier",
       queueProperties,
       decorators,
       processor
   );
    ```

1. Now when the application runs you should have Xray traces being sent to AWS (assuming you have set up Xray in your application correctly).

## Example

For an example using Spring, look at [aws-xray-spring-example](../../../examples/aws-xray-spring-example) where the
[README.md](../../../examples/aws-xray-spring-example/README.md) provides steps to test a full application with Xray.
