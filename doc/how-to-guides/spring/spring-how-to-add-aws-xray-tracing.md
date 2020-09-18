# Spring - How to add AWS Xray Tracing

_This guide assumes that you have knowledge of Xray and how it works. If not take a look at their tutorials online._

## Internal Background

For more information about each component this is auto configuring for you, take a look at the
[Core - How to add AWS Xray Tracing - Internal Details](../core/core-how-to-add-aws-xray-tracing.md#internal-details) section.

## Steps

1.  Add the [AWS XRay Spring Boot Extension](../../../extensions/aws-xray-extension/spring-boot) as a dependency. This will auto configure
    a [BasicXrayMessageProcessingDecorator](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/decorator/BasicXrayMessageProcessingDecorator.java)
    and a [SqsAsyncClientProvider](../../../spring/spring-api/src/main/java/com/jashmore/sqs/spring/client/SqsAsyncClientProvider.java) which will wrap
    the `SqsAsyncClient` in a
    [XrayWrappedSqsAsyncClient](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/XrayWrappedSqsAsyncClient.java).

    ```xml
    <dependency>
        <groupId>com.jashmore</groupId>
        <artifactId>aws-xray-extension-spring-boot</artifactId>
        <version>${dynamic-sqs-listener.version}</version>
    </dependency>
    ```

1.  **[Optional]** If you want tracing information to be included when sending messages to AWS, add the
    [AWS Xray SDK V2 Instrumentor](https://github.com/aws/aws-xray-sdk-java/tree/master/aws-xray-recorder-sdk-aws-sdk-v2-instrumentor) dependency.

    ```xml
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-xray-recorder-sdk-aws-sdk-v2-instrumentor</artifactId>
        <version>${aws-xray.version}</version>
    </dependency>
    ```

## Example

For an example using Spring Boot, look at [aws-xray-spring-example](../../../examples/aws-xray-spring-example). The
[README.md](../../../examples/aws-xray-spring-example/README.md) provides steps to test a full application with Xray.

## Other guides

This Spring Boot dependency is actually very simple and therefore, if you need to do complicated configurations, I would just recommend depending
on the [AWS XRay Core Extension](../../../extensions/aws-xray-extension/core) directly and configuring your application similar to how
the [SqsListenerXrayConfiguration](../../../extensions/aws-xray-extension/spring-boot/src/main/java/com/jashmore/sqs/extensions/xray/spring/SqsListenerXrayConfiguration.java)
builds the beans.

### Send internal library Xray traces

By default, all the requests to receive messages and other internal calls by this library will not be sent to Xray. To override this you can define a
custom [ClientSegmentMutator](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/ClientSegmentMutator.java).

```java
@Configuration
public class MyConfiguration {

    @Bean
    public ClientSegmentMutator clientSegmentMutator() {
        return segment -> {
            // any customisations you want to do here. Nothing is fine too.
        };
    }
}

```

This will remove the
default [UnsampledClientSegmentMutator](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/UnsampledClientSegmentMutator.java)
that is auto configured.

### Naming the message listener segments

Each segment created will use the `spring.application.name` as the name of the segment and if that is not set, it will use `message-listener` as the
segment names. If you require a custom name, you can build your
own [BasicXrayMessageProcessingDecorator](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/decorator/BasicXrayMessageProcessingDecorator.java)
bean.

```java
@Configuration
public class MyConfiguration {

    @Bean
    public ClientSegmentMutator clientSegmentMutator() {
        return new BasicXrayMessageProcessingDecorator(
            BasicXrayMessageProcessingDecorator.Options
                .builder()
                .segmentNamingStrategy(new StaticDecoratorSegmentNamingStrategy("my-custom-name"))
                .build()
        );
    }
}

```

### Using a custom `AWSXRayRecorder`

By default, the `AWSXRay.getGlobalRecorder()` is used as the recorder for communicating to Xray. If you need to replace this with a custom one, e.g. one
communicating with a local Xray daemon, you can create a new bean with the `sqsXrayRecorder` qualifier. For example, here is how you can point to a local
daemon:

```java
@Configuration
public class MyConfiguration {

    @Bean
    @Qualifier("sqsXrayRecorder")
    public AWSXRayRecorder recorder() throws IOException {
        final DaemonConfiguration daemonConfiguration = new DaemonConfiguration();
        daemonConfiguration.setDaemonAddress("127.0.0.1:5678");
        return AWSXRayRecorderBuilder.standard().withEmitter(Emitter.create(daemonConfiguration)).build();
    }
}

```

### Communicating with Multiple AWS Accounts

As seen in [Spring - How to connect to multiple AWS Accounts](spring-how-to-connect-to-multiple-aws-accounts.md), you can create a custom
[SqsAsyncClientProvider](../../../spring/spring-api/src/main/java/com/jashmore/sqs/spring/client/SqsAsyncClientProvider.java) to communicate with multiple
SQS queues across multiple accounts. If you are have included
the [AWS Xray SDK V2 Instrumentor](https://github.com/aws/aws-xray-sdk-java/tree/master/aws-xray-recorder-sdk-aws-sdk-v2-instrumentor) dependency you will
need to make sure to wrap each `SqsAsyncClient` in an
[XrayWrappedSqsAsyncClient](../../../extensions/aws-xray-extension/core/src/main/java/com/jashmore/sqs/extensions/xray/client/XrayWrappedSqsAsyncClient.java).

```java
@Configuration
public class MyConfig {
    @Bean
    public SqsAsyncClientProvider sqsAsyncClientProvider() {
        final ClientSegmentNamingStrategy namingStrategy = new StaticClientSegmentNamingStrategy("service-name");
        final AWSXRayRecorder firstAccountRecorder = ...;
        final AWSXRayRecorder secondAccountRecorder = ...;
        // this client will be used if there is no client identifier for the listener. Note that this can be null
        // and in this case listenerForDefaultClient above will fail to wrap
        final SqsAsyncClient defaultClient = ...;

        final SqsAsyncClient firstClient = ...;
        final SqsAsyncClient secondClient = ...;

        return new DefaultSqsAsyncClientProvider(
            new XrayWrappedSqsAsyncClient(Options.builder()
                    .delegate(defaultClient)
                    .recorder(firstAccountRecorder)
                    .clientNamingStrategy(namingStrategy)
                    .build()),
            ImmutableMap.of(
                "firstClient", new XrayWrappedSqsAsyncClient(Options.builder()
                         .delegate(firstClient)
                         .recorder(firstAccountRecorder)
                         .clientNamingStrategy(namingStrategy)
                         .build()),
                "secondClient", new XrayWrappedSqsAsyncClient(Options.builder()
                         .delegate(secondClient)
                         .recorder(secondAccountRecorder)
                         .clientNamingStrategy(namingStrategy)
                         .build())
            )
       );
    }
}
```
