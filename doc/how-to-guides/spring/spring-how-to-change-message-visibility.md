# Spring - How to change message visibility

By default, the queue listener will use the message visibility defined in the SQS queue, however if you wish to override the visibility for all messages process in this listener you can set this in the listener annotation.

### Example

```java
public class MyClass {

    private final SomeService someService;

    public MyClass(final SomeService someService) {
        this.someService = someService;
    }

    @QueueListener(value = "${queueUrl}", messageVisibilityTimeoutInSeconds = 5)
    public void myMethod(@Payload final String payload) {
        someService.methodThatTakesLong(payload);
    }
}

```

If the message processing could take a longer period than this and you wish to dynamically extend the visibility while it is processing, take a look at the [Spring - How to extend message visibility during processing](spring-how-to-extend-message-visibility-during-processing.md)
