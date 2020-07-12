# Core - How to extend message visibility during processing

When processing a message, the visibility defines when SQS should deem it a failure and place it back onto the queue
for reprocessing (if there is a re-drive policy). If it is desirable to extend the visibility time throughout the execution, a
[VisibilityExtender](../../../api/src/main/java/com/jashmore/sqs/processor/argument/VisibilityExtender.java) can be included
as a parameter.

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
