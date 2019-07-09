# Core - How to implement custom message retrieval logic
The [MessageRetriever](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) handles the logic for how to
get messages from the SQS Queue.  This is the most commonly updated part of the library as this is where the most performance improvements
could be utilised.

## Examples
To learn how to create your own [MessageRetriever](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) it
is useful to take a look at how they are built into the core library. It can be not as straight forward to do as it involves dealing with concurrency and
is written in a non blocking fashion.  The current core implementations can be found in the
[retriever package](../../../java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/retriever).

Once you have built your own retriever you can see how this can be integrated into the framework by looking in the [examples](../../../examples) directory
and more specifically in the [core-examples module](../../../examples/core-examples).

### Integrating the new message retriever into the spring app
If you are using the Spring Starter for this, you can take a look at
[Spring - How to add custom queue wrapper](../spring/spring-how-to-add-own-queue-listener.md) for details on integrating this into the spring application.
