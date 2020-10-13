# Core - How to create a message processing decorator

The [MessageProcessingDecorator](../../../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java) is used
to wrap the processing of the messages with extra functionality like logging, metrics, etc. This guide provides some examples of message decorators and
then how to use them.

## Synchronous vs Asynchronous

The one bit of complexity with these decorators is handling message listeners that are either synchronous or asynchronous in nature. Therefore, care should
be taken when using ThreadLocals or other thread based variables as it could cause unintended outcomes.

### Synchronous Message Listeners

Synchronous message listeners are when the message listener does not return a `CompletableFuture` and therefore runs all the processing in the current thread.
In this scenario, the message processing callbacks will be run on the same thread. For example, given this example implementation you can see which
callbacks will be run on which thread:

```java
public class ExampleMessageProcessingDecorator implements MessageProcessingDecorator {

    @Override
    public void onPreMessageProcessing(MessageProcessingContext context, Message message) {
        // message-listener-thread
    }

    @Override
    public void onMessageProcessingFailure(MessageProcessingContext context, Message message, Throwable throwable) {
        // message-listener-thread
    }

    @Override
    public void onMessageProcessingSuccess(MessageProcessingContext context, Message message, Object object) {
        // message-listener-thread
    }

    @Override
    public void onMessageProcessingThreadComplete(MessageProcessingContext context, Message message) {
        // message-listener-thread
    }

    @Override
    public void onMessageResolvedSuccess(MessageProcessingContext context, Message message) {
        // non message-listener-thread
    }

    @Override
    public void onMessageResolvedFailure(MessageProcessingContext context, Message message, Throwable throwable) {
        // non message-listener-thread
    }
}

```

### Asynchronous Message Listener

Asynchronous message listeners are when the message listener returns a `CompletableFuture` and will mark the message has successfully being processed when
the future is resolved. In this scenario, the message processing callbacks will not be run on the same thread as the message listener. For example, given
this implementation you can see which callbacks will be run on which thread:

```java
public class ExampleMessageProcessingDecorator implements MessageProcessingDecorator {

    @Override
    public void onPreMessageProcessing(MessageProcessingContext context, Message message) {
        // message-listener-thread
    }

    @Override
    public void onMessageProcessingFailure(MessageProcessingContext context, Message message, Throwable throwable) {
        // not guaranteed to be the message-listener-thread
        // It will be whatever thread the message listener is running the message processing on
    }

    @Override
    public void onMessageProcessingSuccess(MessageProcessingContext context, Message message, Object object) {
        // not guaranteed to be the message-listener-thread
        // It will be whatever thread the message listener is running the message processing on
    }

    @Override
    public void onMessageProcessingThreadComplete(MessageProcessingContext context, Message message) {
        // message-listener-thread
    }

    @Override
    public void onMessageResolvedSuccess(MessageProcessingContext context, Message message) {
        // non message-listener-thread
    }

    @Override
    public void onMessageResolvedFailure(MessageProcessingContext context, Message message, Throwable throwable) {
        // non message-listener-thread
    }
}

```

## Adding the MessageProcessingDecorators to the message processing

There is a [DecoratingMessageProcessor](../../../core/src/main/java/com/jashmore/sqs/processor/DecoratingMessageProcessor.java) which
wraps a delegate `MessageProcessor` with this decorating logic. You can then use this `MessageProcessor` instead of the delegate in your
`MessageListenerContainer`.

```java
List<MessageProcessingDecorator> decorators = ...;

return new DecoratingMessageProcessor(
        "message-listener-identifier",
        queueProperties,
        decorators,
        new CoreMessageProcessor(
                argumentResolverService,
                queueProperties,
                sqsAsyncClient,
                messageListenerMethod,
                messageListener
        )
);
```

For integrating when running in a Spring application, take a look at
[Spring - How to add custom MessageProcessingDecorators](../spring/spring-how-to-add-custom-message-processing-decorators.md).

## Examples

### Logging

You want all logs in the message listener to contain the message ID of the message.

```java
public class MdcMessageProcessingDecorator implements MessageProcessingDecorator {

    @Override
    public void onPreMessageProcessing(MessageProcessingContext context, Message message) {
        MDC.put("message.id", message.messageId());
    }

    @Override
    public void onMessageProcessingThreadComplete(MessageProcessingContext context, Message message) {
        MDC.remove("message.id");
    }
}

```

### Metrics

You want to monitor the number of messages attempting to be processed as well as the number that were successfully processed.

```java
public class MetricsMessageProcessingDecorator implements MessageProcessingDecorator {

    private final MyMetricsService metrics;

    public MetricsMessageProcessingDecorator(final MyMetricsService metrics) {
        this.metrics = metrics;
    }

    @Override
    public void onPreMessageProcessing(MessageProcessingContext context, Message message) {
        metrics.trackMessageBeingProessed();
    }

    @Override
    public void onMessageResolvedSuccess(MessageProcessingContext context, Message message) {
        metrics.messageSuccessfullyProcessed();
    }
}

```

## Sharing information between callbacks

As we cannot guarantee that all callbacks will be run on the same thread, instead of using a `ThreadLocal` you can use the
[MessageProcessingContext](../../../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingContext.java) to set
attributes for future usage.

```java
@Slf4J
public class MdcMessageProcessingDecorator implements MessageProcessingDecorator {

    @Override
    public void onPreMessageProcessing(MessageProcessingContext context, Message message) {
        long startTime = System.currentTimeMillis();
        context.setAttribute("startTime", startTime);
    }

    @Override
    public void onMessageProcessingSuccess(MessageProcessingContext context, Message message, Object object) {
        long endTime = System.currentTimeMillis();
        log.info("Message processed in {}ms", endTime - (long) context.getAttribute("startTime"));
    }
}

```
