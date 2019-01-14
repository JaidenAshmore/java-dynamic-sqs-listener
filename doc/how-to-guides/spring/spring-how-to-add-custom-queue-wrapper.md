# Spring - How to add a custom QueueWrapper
The [QueueWrapper](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/QueueWrapper.java) is
used to process bean methods to determine whether they should be used to process SQS messages. The most common way to define whether a method should be wrapped
is via an annotation, for example the following method indicates that it should be wrapped in a basic queue listener:

```java
@QueueListener("http://localhost:9432/q/myqueue")
public void messageConsumer(@Payload final String messagePayload) {
    // do something with the message
}
```

There are only a few core [QueueWrapper](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/QueueWrapper.java)s
that are provided and therefore may not cover the use cases that is desired. Therefore, the consumers can define their own wrapper that will be used with the
core ones provided.

## Example Use Case
An application wants to provide a custom annotation to wrap methods with some message listening logic that does not align with the core wrappers provided. For
example, the `SleepingMessageRetriever` built in the [how to implement a custom message retrieval](../core/core-how-to-implement-a-custom-message-retrieval.md).

## Prerequisites
1. This relies on no [QueueContainerService](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/container/QueueContainerService.java)
being provided by the consumer of this framework and therefore the default will be used. See
[QueueListenerAutoConfiguration](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/config/QueueListenerConfiguration.java)
for more information about how the [QueueWrapper](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/QueueWrapper.java)
are collected.

## Steps
For this example, we want to be able to wrap a method in the annotation `SleepingQueueListener` that will use the components of the framework that we have written
our self. This could be our own [MessageRetriever](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) that
we wrote (see [Core - How to implement a custom message retrieval](../core/core-how-to-implement-a-custom-message-retrieval.md)).

1. Create the annotation that will be used to indicate the methods using this queue wrapper.
    ```java
    @Retention(RUNTIME)
    @Target(METHOD)
    public @interface SleepingQueueListener {
           /**
            * The queue URL or name of the queue to listen to.
            */  
           String value();
        
           /**
            * This is the amount of time the thread should be slept before actually retrieving the message.
            */
           Long sleepPeriodInMs();
    }
    ```
1. Implement the [QueueWrapper](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/QueueWrapper.java)
interface with the custom implementation. As we are using annotations to indicate the method to wrap, we can extend the
[AbstractQueueAnnotationWrapper](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/AbstractQueueAnnotationWrapper.java)
to make matching on annotations easier. At this point whenever we enter `wrapMethodContainingAnnotation` we have found a bean with a method
annotated with `SleepingQueueListener` and now we need to create a
[MessageListenerContainer](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/container/MessageListenerContainer.java)
that has the responsibility of handling the spring lifecycle of this queue listener. The
[SimpleMessageListenerContainer](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/container/SimpleMessageListenerContainer.java)
is the simplest (and currently only) implementation of this and is the most likely one that would be used.
    ```java
    public class MySleepingQueueWrapper extends AbstractQueueAnnotationWrapper<SleepingQueueListener> {
        private final ArgumentResolverService argumentResolverService;
     
        public MySleepingQueueWrapper(final ArgumentResolverService argumentResolverService) {   
            this.argumentResolverService = argumentResolverService;
        }
     
        @Override
        protected Class<SleepingQueueListener> getAnnotationClass() {
            return SleepingQueueListener.class;
        }
     
        @Override
        protected MessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method, final SleepingQueueListener annotation) {
            final QueueProperties queueProperties = QueueProperties
                   .builder()
                   .queueUrl( queueResolverService.resolveQueueUrl(annotation.value()))
                   .build();
    
            final MessageProcessor messageProcessor = new DefaultMessageProcessor(argumentResolverService, queueProperties,
                    sqsAsyncClient, method, bean);
            
            final MessageRetriever messageRetriever = new SleepingMessageRetriever(sqsAsyncClient, queueProperties, annotation.sleepPeriodInMs())
    
            final SingleThreadedMessageBroker singleThreadedMessageBroker = new SingleThreadedMessageBroker(messageRetriever, messageProcessor);
            
            return new SimpleMessageListenerContainer(
                    bean.getClass().getName() + "#" + method.getName(),
                    singleThreadedMessageBroker
            );      
        }
    }
    ```
1. Add this bean into the application context by annotating it with the `@Component` annotation or by defining it as a spring bean in a `@Configuration` class.
     ```java
     @Configuration
     public class MyConfiguration {
        @Bean
        public QueueWrapper MySleepingQueueWrapper(final ArgumentResolverService argumentResolverService) {
            return new MySleepingQueueWrapper(argumentResolverService); 
        }
     }
     ``` 