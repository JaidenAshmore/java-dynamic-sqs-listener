# Core - How to implement custom message retrieval logic
As can be seen in the [Core Framework Architecture](../../core_framework_architecture.md) one of the components is the 
[MessageRetriever](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) which handles the logic for how to
get messages from the SQS Queue.  This is the most commonly updated part of the framework as this is where the most performance improvements
could be utilised/

## Example Use Case
An application is listening on a queue that has as very slow rate of messages (1 per day) and the time allowed for the message to be processed
is high (we can allow messages to be picked up every 5 minutes). In this scenario, if a
[PrefetchingMessageRetriever](../../../java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/retriever/prefetch/PrefetchingMessageRetriever.java) is
used to retrieve messages, every 30 seconds a long polling request is sent requesting more messages, which is unlikely to have any messages. Therefore
each day 2,400 requests would be sent to SQS messages which, while still a low amount, could be an unnecessary cost for the application. To mitigate
this a custom [MessageRetriever](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) is written that
will sleep the fetching thread for a specified period (e.g. 5 minutes) before attempting to request for a message, thus reducing the amount of polls each
day to 240, a reduction to 10% of the original.

## Assumptions
- The implementation will assume that only a single thread will be used for message retrieval, see
[SingleThreadedMessageBroker](../../../java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/broker/singlethread/SingleThreadedMessageBroker.java),
and therefore there is no gain in making the retriever an
[AsyncMessageRetriever](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/AsyncMessageRetriever.java).
- This example will also create the implementation in its own module as it is desirable to make this shareable to other places.

## Steps
1. In the modules pom.xml, add a dependency on the java-dynamic-sqs-listener-api which  will provide the interfaces that are needing to be implemented.
    ```xml
    <dependency>
       <groupId>com.jashmore</groupId>
       <artifactId>java-dynamic-sqs-listener-api</artifactId>
       <version>${java.dynamic.sqs.listener.version}</version>
    </dependency>
    ```
1. Create an implementation of the [MessageRetriever](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java)
    ```java
    public class SleepingMessageRetriever implements MessageRetriever {
         private static final Logger log = LoggerFactory.forClass(SleepingMessageRetriever.class);
      
         private final SqsAsyncClient sqsAsyncClient;
         private final QueueProperties queueProperties;
         private final long sleepPeriodInMs;
    
         public SleepingMessageRetriever(final SqsAsyncClient sqsAsyncClient,
                                         final QueueProperties queueProperties,
                                         final long sleepPeriodInMs) {   
             this.sqsAsyncClient = sqsAsyncClient;
             this.queueProperties = queueProperties;
             this.sleepPeriodInMs = sleepPeriodInMs;
         }  
       
         @Override
         public Message retrieveMessage() throws InterruptedException {   
             while (true) {
                 // We will sleep the thread straight away as we don't need to get the message now
                 Thread.sleep(sleepPeriodInMs);
              
                 final ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest
                         .builder()
                         .queueUrl(queueProperties.getQueueUrl())
                         .maxNumberOfMessages(1)
                         .waitTimeSeconds(30)
                         .build();
                 try {
                     final Future<ReceiveMessageResponse> receiveMessageResponseFuture = sqsAsyncClient.receiveMessage(receiveMessageRequest);
                     final ReceiveMessageResponse response = receiveMessageResponseFuture.get();   
                     if (!response.messages().isEmpty()) {
                         return response.messages().get(0); 
                     }
                 } catch (ExecutionException e) {
                     log.error("Error retrieving messages, trying again", e);
                 }
             }
         }   
    }
    ```
1. Now you can integrate this message retriever into the framework just like any other message retriever implementation. See [examples](../../../examples)
for examples on how to integrate the retriever.

### Integrating the new message retriever into the spring app
If you are using the Spring Starter for this, you can take a look at
[Spring - How to add custom queue wrapper](../spring/spring-how-to-add-custom-queue-wrapper.md) for details on integrating this into the spring application.
