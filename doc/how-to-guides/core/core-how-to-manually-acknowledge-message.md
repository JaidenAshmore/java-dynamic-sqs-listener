# Core - How to manually acknowledge messages
When the method executing a message completes without throwing an exception it is acknowledged by deleting it from the queue. There however are use cases
where it is desirable to acknowledge the message during the execution instead of at the end. For example, there is a certain bit of processing that needs
to be done but after that if there is an exception it is desirable not to reprocess the message (if there is a retry policy). To acknowledge a message manually,
an [Acknowledge](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/argument/Acknowledge.java) parameter can be included in the
method signature and used to indicate when the message processing is a success.

**Note that when this parameter is included the message will not be acknowledged at the end of the methods execution, even if there was no exception thrown.**

## Example
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
