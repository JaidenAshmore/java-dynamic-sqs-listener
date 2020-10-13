# Core - How to extend message visibility during processing

When processing a message, the visibility defines when SQS should deem it a failure and place it back onto the queue
for reprocessing (if there is a re-drive policy).

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
[AutoVisibilityExtenderMessageProcessingDecorator](../../../core/src/main/java/com/jashmore/sqs/decorator/AutoVisibilityExtenderMessageProcessingDecorator.java)
was added to the core library which will handle the extension of this message if it is taking a while to process. If it goes too long it will interrupt
the processing of the message.

Note that this decorator only works for synchronous message listeners and will have unsafe functionality when used with an asynchronous message listener,
e.g. one which returns a `CompletableFuture`.

```java
public class MyClass {

    public static void main(String[] args) throws InterruptedException {
        final SqsAsyncClient sqsAsyncClient = SqsAsyncClient.create(); // or your own custom client
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl("${insert.queue.url.here}").build();
        final MessageProcessingDecorator autoVisibilityExtender = new AutoVisibilityExtenderMessageProcessingDecorator(
            sqsAsyncClient,
            queueProperties,
            new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {
                @Override
                public Duration visibilityTimeout() {
                    return Duration.ofMinutes(1);
                }

                @Override
                public Duration maxDuration() {
                    return Duration.ofMinutes(5);
                }

                @Override
                public Duration bufferDuration() {
                    return Duration.ofSeconds(30);
                }
            }
        );
        final MessageListenerContainer container = new CoreMessageListenerContainer(
            "listener-identifier",
            () -> new ConcurrentMessageBroker(StaticConcurrentMessageBrokerProperties.builder().concurrencyLevel(10).build()),
            () ->
                new PrefetchingMessageRetriever(
                    sqsAsyncClient,
                    queueProperties,
                    StaticPrefetchingMessageRetrieverProperties.builder().desiredMinPrefetchedMessages(10).maxPrefetchedMessages(20).build()
                ),
            () ->
                new DecoratingMessageProcessor(
                    "listener-identifier",
                    queueProperties,
                    Collections.singletonList(autoVisibilityExtender),
                    new LambdaMessageProcessor(
                        sqsAsyncClient,
                        queueProperties,
                        message -> {
                            try {
                                someLongFileIOMethod();
                            } catch (InterruptionException e) {
                                // the message took to long and it was interrupted
                            }
                        }
                    )
                ),
            () -> new BatchingMessageResolver(queueProperties, sqsAsyncClient)
        );
        container.start();
        Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
        Thread.currentThread().join();
    }
}

```

In the example above it will keep the message's visibility to be 1 minute and making sure every 30 seconds the visibility timeout will be extended. If
the message hasn't been processed within 5 minutes, the thread processing this message will be interrupted.
