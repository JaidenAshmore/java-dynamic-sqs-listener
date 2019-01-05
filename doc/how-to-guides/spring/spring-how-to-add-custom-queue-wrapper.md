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

## Prerequisites
1. This relies on no [QueueContainerService](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/container/QueueContainerService.java)
being provided by the consumer of this framework and therefore the default will be used. See
[QueueListenerAutoConfiguration](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-core/src/main/java/com/jashmore/sqs/spring/config/QueueListenerAutoConfiguration.java)
for more information about how the [QueueWrapper](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/QueueWrapper.java)
are collected.

## Steps
1. Implement the [QueueWrapper](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/QueueWrapper.java)
interface with the custom implementation.
    ```java
    public class ConsumerQueueWrapper implements QueueWrapper {
        @Override
        public boolean canWrapMethod(Method method) {
            // This will be called on every bean method defined in the application and this can be determined whether it is eligible for wrapping
        }
     
        @Override
        public MessageListenerContainer wrapMethod(Object bean, Method method) {
            // For methods that can be wrapped (determined above), build a container that defines the core components needed to wrap this method in the
            // message listener container.
        }   
    }
    ```
1. Add this bean in the Spring Context by annotating it with a `@Component` (or other equivalent annotation) or by providing it as a bean in a `@Configuration`
class.
     ```java
     @Configuration
     public class MyConfiguration {
        @Bean
        public QueueWrapper customConsumerQueueWrapper() {
            return new ConsumerQueueWrapper(); 
        }   
     }
  
At this point the default [QueueContainerService](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/container/QueueContainerService.java)
should contain this custom queue wrapper and it should be applied during execution of the framework.