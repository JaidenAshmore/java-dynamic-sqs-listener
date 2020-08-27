# Core - How to mark messages as successfully processed

There are currently a few ways that can be used to mark that a message has been successfully processed and therefore it can be resolved (deleted from the
SQS Queue).

## Returning without exception

The easiest method is to just make your method not throw an exception when it is executed. In this scenario the
[MessageProcessor](../../../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) sees that the processing was successful, by not throwing
an exception, and it will resolve the message.

### Example

```java
public class MyClass {
    private final SomeService someService;

    public MyClass(final SomeService someService) {
        this.someService = someService;
    }

    public void myMethod(@Payload final String payload) {
        someService.importantProcessing(payload);
    }
}

```

## Including an Acknowledge Object in the method signature

When the method includes an [Acknowledge](../../../api/src/main/java/com/jashmore/sqs/processor/argument/Acknowledge.java) parameter,
it is now the message listener's responsibility to manually resolve the message by calling
`acknowledge.acknowledgeSuccessful()`. If this is never done then the message will not be deleted and will potentially be replaced into the queue
depending on the re-drive policy.

_Note: that this returns a `CompletableFuture` and will only guarantee to be completed if that future resolves successfully._

### Example

```java
public class MyClass {
    private final SomeService someService;

    public MyClass(final SomeService someService) {
        this.someService = someService;
    }

    public void myMethod(@Payload final String payload, final Acknowledge acknowledge) {
        someService.importantProcessing(payload);

        acknowledge.acknowledgeSuccessful(); // If there is an exception thrown from now on the message will still be a success

        someService.unimportantProcessing(payload);
    }
}

```

## The method returns a CompletableFuture

When the method does not include an [Acknowledge](../../../api/src/main/java/com/jashmore/sqs/processor/argument/Acknowledge.java)
parameter, and it returns a `CompletableFuture`, then the message will be successfully resolved when that future resolves.

**Warning: If this `CompletableFuture` is never completed, the [MessageProcessor](../../../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java)
will wait forever and ultimately you will have no more messages processing as all threads are waiting for the future to complete.**

### Example

```java
public class MyClass {
    private final SomeService someService;

    public MyClass(final SomeService someService) {
        this.someService = someService;
    }

    public CompletableFuture<?> myMethod(@Payload final String payload) {
        return someService.importantProcessingAsync(payload);
    }
}

```
