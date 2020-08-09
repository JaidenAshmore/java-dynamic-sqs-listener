# Core - How to add Brave Tracing

## Steps

1.  Make sure you have the Brave tracing dependency configured, e.g. via Spring Sleuth. See their documentation on how to do this.
1.  Add the [brave-extension-core](../../../extensions/brave-extension/core) module

    ```xml
    <dependency>
        <groupId>com.jashmore</groupId>
        <artifactId>brave-extension-core</artifactId>
        <version>${java.dynamic.sqs.listener.version}</version>
    </dependency>
    ```

1.  Wrap your [MessageProcessor](../../../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) with
    the [DecoratingMessageProcessor](../../../core/src/main/java/com/jashmore/sqs/processor/DecoratingMessageProcessor.java).

    ```java
    final List<MessageProcessingDecorators> messageProcessingDecorators = ImmutableList.of(
          new BraveMessageProcessingDecorator(tracing)
    );
    final CoreMessageProcessor delegateProcessor = ...
    return new DecoratingMessageProcessor(identifier, queueProperties, messageProcessingDecorators, delegateProcessor);
    ```

## Example

See the [core-example](../../../examples/core-example) for a basic application that is running with tracing enabled automatically.

## Including Brave information in your SQS messages

If you want to include the Trace information into your SQS message so that the message listener continues the trace,
you can use the [sqs-brave-tracing](../../../util/sqs-brave-tracing) Utility module to add the tracing information to the
SQS Message Attributes.

An example of this being done is in the
[spring-sleuth-example](../../../examples/spring-sleuth-example/src/main/java/com/jashmore/sqs/examples/sleuth/Application.java).
