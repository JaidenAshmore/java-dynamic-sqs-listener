# Spring - How to Connect to multiple AWS Accounts
There may be a scenario where you need to connect to multiple queues across multiple AWS Accounts. In this scenario you would
need to provide multiple `SqsAsyncClients` and for each queue listener you will need to indicate which one is desired. For a full
example take a look at the [Multiple AWS Accounts Example](../../../examples/spring-multiple-aws-account-example) which shows you how to
connect to two locally running ElasticMQ servers.

## Steps
1. Create some queues that will use specific `SqsAsyncClient`s identified by an id.
    ```java
    public class MyMessageListeners {
        // This uses the "default" SqsAsyncClient which may not be present
        @QueueListener("queueNameForDefaultListener")
        public void listenerForDefaultClient(@Payload String messageBody) {
         
        }
     
        // This uses the "firstClient" SqsAsyncClient
        @QueueListener(value = "queueNameForFirstClient", sqsClient = "firstClient")
        public void queueNameListenerForFirstClient(@Payload String messageBody) {
         
        }
        
        // This uses the "firstClient" SqsAsyncClient
        @QueueListener(value = "anotherQueueNameForFirstClient", sqsClient = "firstClient")
        public void anotherQueueNameListenerForFirstClient(@Payload String messageBody) {
         
        }
     
        // This uses the "secondClient" SqsAsyncClient
        @QueueListener(value = "queueNameForSecondClient", sqsClient = "secondClient")
        public void queueNameListenerForSecondClient(@Payload String messageBody) {
         
        }
    }
    ```
1. You will need to add to your `@Configuration` a bean of type
[SqsAsyncClientProvider](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/client/SqsAsyncClientProvider.java)
which will provide all of the `SqsAsyncClient`s for the queues above.
    ```java
    @Configuration
    public class MyConfig {
        @Bean
        public SqsAsyncClientProvider sqsAsyncClientProvider() {
           // this client will be used if there is no client identifier for the listener. Note that this can be null
           // and in this case listenerForDefaultClient above will fail to wrap 
           final SqsAsyncClient defaultClient = ...;
        
           final SqsAsyncClient firstClient = ...;
           final SqsAsyncClient secondClient = ...;
        
           return new DefaultSqsAsyncClientProvider(
                defaultClient,
                ImmutableMap.of(
                     "firstClient", firstClient,
                     "secondClient", secondClient  
                )
           );
        }   
    }
    ```
