# Core - How to add AWS XRay Tracing

_This guide assumes that you have knowledge of Xray and how it works. If not take a look at their tutorials online._

## Steps

1. Add the [AWS XRay Core Extension](../../../extensions/aws-xray-extension/core) as a dependency.

    ```xml
        <dependency>
            <groupId>com.jashmore</groupId>
            <artifactId>aws-xray-extension-core</artifactId>
            <version>${dynamic-sqs-listener.version}</version>
        </dependency>
    ```

1. Wrap the [MessageProcessor](../../../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) with a
[DecoratingMessageProcessor](../../../core/src/main/java/com/jashmore/sqs/processor/DecoratingMessageProcessor.java) with an instance of the
[BasicXrayMessageProcessingDecorator](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/decorator/BasicXrayMessageProcessingDecorator.java).
This will extract the tracing header from the SQS message and begin a Xray Segment.

    ```java
    MessageProcessor processor = ...;
    List<MessageProcessingDecorator> decorators = new ArrayList<>();
    decorators.add(new BasicXrayMessageProcessingDecorator(Options.builder()
           .recorder(AWSXRay.getGlobalRecorder())
           .segmentNamingStrategy(new StaticDecoratorSegmentNamingStrategy("service-name"))
           .build());
    DecoratingMessageProcessor decoratingMessageProcessor = new DecoratingMessageProcessor(
       "identifier",
       queueProperties,
       decorators,
       processor
    );
    ```

1. Now when the application runs any tracing in the message will be continued in the message listener.

## Integration with AWS Xray Instrumentor

If you want tracing information to be included when making client calls to AWS, you can add the
[AWS Xray SDK V2 Instrumentor](https://github.com/aws/aws-xray-sdk-java/tree/master/aws-xray-recorder-sdk-aws-sdk-v2-instrumentor) dependency. 

```xml
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-xray-recorder-sdk-aws-sdk-v2-instrumentor</artifactId>
        <version>${aws-xray.version}</version>
    </dependency>
```

This requires the `SqsAsyncClient` to be used inside Xray segments otherwise a segment not found exceptions will be thrown. To get around this, wrap
the `SqsAsyncClient` in
an [XrayWrappedSqsAsyncClient](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/XrayWrappedSqsAsyncClient.java).
This will make sure every call to SQS will make sure the Xray Segment has been started.

```java
SqsAsyncClient client = ...;
SqsAsyncClient xrayClient = new XrayWrappedSqsAsyncClient(Options.builder()
        .delegate(client)
        .recorder(AWSXRay.getGlobalRecorder())
        .clientNamingStrategy(new StaticClientSegmentNamingStrategy("service-name"))
        .build());
```

## Example

For an example using Spring, look at [aws-xray-spring-example](../../../examples/aws-xray-spring-example) where the
[README.md](../../../examples/aws-xray-spring-example/README.md) provides steps to test a full application with Xray.

## Internal Details

Some background on each of the components above and why you need them.

### [BasicXrayMessageProcessingDecorator](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/decorator/BasicXrayMessageProcessingDecorator.java)

This [MessageProcessingDecorator](../../../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java) wraps the message listener and provides
the functionality to hook into existing Xray traces contained in the message. This decorator will start a new segment and, if there is
an `AwsTraceHeader` message attribute, it will join the trace together. If the message is from another AWS service (like SNS) or from another service, the
consumer should now be able to see this in the Xray Trace.

### [XrayWrappedSqsAsyncClient](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/XrayWrappedSqsAsyncClient.java)

This `SqsAsyncClient` is needed to make sure all calls to SQS do not result in exceptions when you integrate the
[AWS Xray SDK V2 Instrumentor](https://github.com/aws/aws-xray-sdk-java/tree/master/aws-xray-recorder-sdk-aws-sdk-v2-instrumentor) dependency into your
application, as all requests out to the SQS server will automatically be traced and requires a segment to have been started. As this SQS Listener library
maintains its own threads and does not start Xray segments, all calls out to SQS will throw exceptions due to a missing segment.
The [XrayWrappedSqsAsyncClient](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/XrayWrappedSqsAsyncClient.java)
protects against these errors by making sure there is always a segment present when making a call with the client by creating one for you.

