# Core - How to extend message visibility during processing
When processing a message the visibility is originally set for the message which indicates when SQS should deem it a failure and place it back onto the queue
for reprocessing (if there is a replay policy). The default implementation of the [MessageProcessor](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/processor/MessageProcessor.java)
removes the message when the method has executed without an exception. However, extra time may be required than was initial provided because of a long
running process and in this case the message's visibility should extend while the message is continuing to be processed. To do this a
[VisibilityExtender](../../../java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/visibility/VisibilityExtender.java) has
been provided in the core framework which can be used to extend a message's visibility during processing.

## Example
```java
public class MyClass {
    private final SomeService someService;
    
    public MyClass(final SomeService someService) {
        this.someService = someService;
    }
    
    public void myMethod(@Payload final String payload, final VisibilityExtender visibilityExtender) {
        someService.methodThatTakesLong(payload);
        
        visibilityExtender.extend(30); // the message's visibility has been extended by 30 seconds
        
        someService.methodThatTakesLongToo(payload);
    }
}
```