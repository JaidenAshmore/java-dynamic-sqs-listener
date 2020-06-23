# Core - How to mark messages as successfully processed
There are currently a few ways that can be used to mark that a message has been successfully processed and therefore it can be resolved (deleted from the
SQS Queue).

## Returning without exception
The easiest method is to just make your method not throw an exception when it is executed. In this scenario the
[MessageProcessor](../../../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java) sees that it was successfully
processed and it will mark it for being resolved.

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
When the method includes an [Acknowledge](../../../api/src/main/java/com/jashmore/sqs/processor/argument/Acknowledge.java) parameter
then it has been indicated that the method will have the responsibility of deleting the message from the queue when it is finished processing by calling
`acknowledge.acknowledgeSuccessful()`. If this is never done then the message will not be deleted and will potentially be replaced into the queue
depending on the redrive policy.

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
parameter and it returns a `CompletableFuture` then the message will be successfully resolved when that future resolves.  Note that in this scenario if
this `CompletableFuture` is never resolved or rejected the [MessageProcessor](../../../api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java)
will wait forever and therefore it is important to also have these resolved or rejected.

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


