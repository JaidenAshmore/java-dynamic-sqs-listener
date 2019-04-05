# Spring - How to use the Custom Queue Listener

### Using custom library components in queue listener annotation
The [CustomQueueListener](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/container/custom/CustomQueueListener.java)
is provided to allow for consumers to create queue listeners with their own implementations of the library components via factory beans. This is not
the most intuitive way to set up a queue listener as building all of these factories are a bit of pain and it would probably be easier to just define
your own annotation, see [How to add your own Queue Listener](spring-how-to-add-own-queue-listener.md).  Regardless, the steps to easily set up a custom
Queue Listener are:

1. Define factories that can construct all of the components of the library needed for this queue listener. The easiest way is to use lambdas but actual
implementations of the factory is appropriate too. See that each factory will build the main components of the library.
    ```java
    @Configuration
    public class MyConfig {
        @Bean
        public MessageRetrieverFactory myMessageRetrieverFactory(final SqsAsyncClient sqsAsyncClient) {
            return (queueProperties) -> new MyCustomMessageRetriever(queueProperties);
        }

        @Bean
        public MessageProcessorFactory myMessageProcessorFactory(final ArgumentResolverService argumentResolverService,
                                                                 final SqsAsyncClient sqsAsyncClient) {
            // In this scenario we return a core implementation
            return (queueProperties, bean, method) -> {   
                // This will remove the messages straight away after being successfully processed. To batch messages deletions
                // use the BatchingMessageResolver instead
                final MessageResolver messageResolver = new IndividualMessageResolver(queueProperties, sqsAsyncClient);    
                return new DefaultMessageProcessor(argumentResolverService, queueProperties, messageResolver, method, bean);    
            };   
        }
        
        @Bean
        public MessageBrokerFactory myMessageBrokerFactory() {
            return (messageRetriever, messageProcessor) -> new MyMessageBroker(messageRetriever, messageProcessor);   
        }
    }
    ```
1. Wrap a method with the
[CustomQueueListener](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/container/custom/CustomQueueListener.java)
containing the bean names of the factories that you made above, e.g. `myMessageRetrieverFactory`.
    ```java
    @Service
    public class MyService {
        @CustomQueueListener(value = "${insert.queue.url.here}",  
               messageBrokerFactoryBeanName = "myMessageBrokerFactory",
               messageProcessorFactoryBeanName = "myMessageProcessorFactory",
               messageRetrieverFactoryBeanName = "myMessageRetrieverFactory"
        ) 
        public void messageListener(@Payload final String payload) {
            // process the message payload here
        }
    }
    ```
    