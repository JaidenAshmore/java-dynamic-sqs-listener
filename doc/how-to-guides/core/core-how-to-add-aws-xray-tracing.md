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

1. **[Optional]** If you want tracing information to be included when sending messages to AWS, add the
[AWS Xray SDK V2 Instrumentor](https://github.com/aws/aws-xray-sdk-java/tree/master/aws-xray-recorder-sdk-aws-sdk-v2-instrumentor) dependency.

    ```xml
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-xray-recorder-sdk-aws-sdk-v2-instrumentor</artifactId>
            <version>${aws-xray.version}</version>
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

1. Now when the application runs any tracing in the message will be continued in the message listener.

## Example

For an example using Spring, look at [aws-xray-spring-example](../../../examples/aws-xray-spring-example) where the
[README.md](../../../examples/aws-xray-spring-example/README.md) provides steps to test a full application with Xray.

## Internal Details

Some background on each of the components above and why you need them.

### [BasicXrayMessageProcessingDecorator](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/decorator/BasicXrayMessageProcessingDecorator.java)

This [MessageProcessingDecorator](../../../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java) wraps the message listener and provides
the functionality to hook into existing Xray traces. This decorator will start a new segment and if there is an `AwsTraceHeader` message attribute it will
join the trace together. If the message is from another AWS service (like SNS) or from another service, the consumer should now be able to see this in the
Xray Trace. Note that if you are using the [AWS Xray SDK V2 Instrumentor](https://github.com/aws/aws-xray-sdk-java/tree/master/aws-xray-recorder-sdk-aws-sdk-v2-instrumentor)
and do not include this dependency, when you use an AWS Client during message processing you will get an exception due to a missing segment.

### [XrayWrappedSqsAsyncClient](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/XrayWrappedSqsAsyncClient.java)

This `SqsAsyncClient` is needed to make sure all calls to SQS do not result in exceptions if you integrate the
[AWS Xray SDK V2 Instrumentor](https://github.com/aws/aws-xray-sdk-java/tree/master/aws-xray-recorder-sdk-aws-sdk-v2-instrumentor) dependency into your
application, as all requests out to the SQS server will automatically be traced.

The [AWS Xray SDK V2 Instrumentor](https://github.com/aws/aws-xray-sdk-java/tree/master/aws-xray-recorder-sdk-aws-sdk-v2-instrumentor) will auto inject
a `TracingInterceptor` that starts a new subsegment for each call
to SQS and if there is no segment already started you will get an exception. If you are using the client via an HTTP thread, and it has gone through
the [AWSXRayServletFilter](https://github.com/aws/aws-xray-sdk-java/blob/master/aws-xray-recorder-sdk-core/src/main/java/com/amazonaws/xray/javax/servlet/AWSXRayServletFilter.java),
you will not have a problem as a segment has already been started. However, in this SQS Listener library, the message retrieval and other functionality
are all running on their own thread pool and therefore any calls using a basic `SqsAsyncClient` will result in exceptions due to a missing segment.
The [XrayWrappedSqsAsyncClient](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/XrayWrappedSqsAsyncClient.java)
protects against these errors by making sure there is always a segment present when making a call with the client.
