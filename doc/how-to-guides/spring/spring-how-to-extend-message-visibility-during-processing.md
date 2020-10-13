# Spring - How to extend message visibility during processing

## Manually Extending

A message listener may manually extend the timeout by using the
[VisibilityExtender](../../../api/src/main/java/com/jashmore/sqs/processor/argument/VisibilityExtender.java) as a parameter.

### Example

```java
public class MyClass {

    private final SomeService someService;

    public MyClass(final SomeService someService) {
        this.someService = someService;
    }

    @QueueListener("${queueUrl}")
    public void myMethod(@Payload final String payload, final VisibilityExtender visibilityExtender) {
        someService.methodThatTakesLong(payload);

        visibilityExtender.extend(30); // the message's visibility has been extended by 30 seconds

        someService.methodThatTakesLongToo(payload);
    }
}

```

## Automatically Extending the visibility

It may be undesirable to have to manually handle the visibility timeout as it is more development effort on the consumer's side, and it may not even be easily
possible due to the consumption of third party libraries. The
[@AutoVisibilityExtender](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/decorator/visibilityextender/AutoVisibilityExtender.java) annotation
can be used with one of the core queue listener annotations.

Note that this decorator only works for synchronous message listeners and will have unsafe functionality when used with an asynchronous message listener,
e.g. one which returns a `CompletableFuture`.

```java
@Service
public class MyMessageListener {

    @QueueListener("${insert.queue.url.here}")
    @AutoVisibilityExtender(visibilityTimeoutInSeconds = 60, maximumDurationInSeconds = 300, bufferTimeInSeconds = 10)
    public void processMessage(@Payload final String payload) {
        // process the message payload here
    }
}

```
