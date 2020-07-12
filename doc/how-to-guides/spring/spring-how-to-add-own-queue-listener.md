# Spring - How to add your own Queue Listener

The [MessageListenerContainerFactory](../../../spring/spring-api/src/main/java/com/jashmore/sqs/spring/container/MessageListenerContainerFactory.java) is
used to process bean methods to determine whether they should be used to process SQS messages. The most common way to define whether a method should be wrapped
is via an annotation, for example the following method indicates that it should be wrapped in a basic queue listener:

```java
@Component
public class MyService {
    @QueueListener("http://localhost:9432/q/myqueue")
    public void messageConsumer(@Payload final String messagePayload) {
        // do something with the message
    }
}
```

There are only a few core implementations of the [MessageListenerContainerFactory](../../../spring/spring-api/src/main/java/com/jashmore/sqs/spring/container/MessageListenerContainerFactory.java)
and therefore there may be use cases that are not covered. Consumers can define their own factory that will be used alongside the core ones provided.

## Example Use Case

An application wants to provide a custom annotation to wrap methods with some message listening logic that does not align with the core wrappers provided.

## Prerequisites

1. This relies on no custom [MessageListenerContainerCoordinator](../../../spring/spring-api/src/main/java/com/jashmore/sqs/spring/container/MessageListenerContainerCoordinator.java)
being provided by the consumer of this framework and therefore the default will be used. See
[QueueListenerConfiguration](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/config/QueueListenerConfiguration.java)
for more information about how the [MessageListenerContainerFactory](../../../spring/spring-api/src/main/java/com/jashmore/sqs/spring/container/MessageListenerContainerFactory.java)
are collected.

## Steps

For this example, we want to be able to wrap a method in the annotation `SleepingQueueListener` that will use the components of the framework that we have written
our self. This could be our own [MessageRetriever](../../../api/src/main/java/com/jashmore/sqs/retriever/MessageRetriever.java) that
we wrote.

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

1. Implement the [MessageListenerContainerFactory](../../../spring/spring-api/src/main/java/com/jashmore/sqs/spring/container/MessageListenerContainerFactory.java)
interface with the custom implementation. As we are using annotations to indicate the method to wrap, we can extend
the [AbstractAnnotationMessageListenerContainerFactory](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/AbstractAnnotationMessageListenerContainerFactory.java)
to make matching on annotations easier. At this point whenever we enter `wrapMethodContainingAnnotation` we have found a bean with a method
annotated with `SleepingQueueListener` and now we need to create
a [MessageListenerContainer](../../../api/src/main/java/com/jashmore/sqs/container/MessageListenerContainer.java)
that has the responsibility of handling the spring lifecycle of this queue listener. The
[CoreMessageListenerContainer](../../../core/src/main/java/com/jashmore/sqs/container/CoreMessageListenerContainer.java)
is the simplest (and currently only) implementation of this and is the most likely one to use.

    ```java
    public class MySleepingMessageListenerContainerFactory extends AbstractAnnotationMessageListenerContainerFactory<SleepingQueueListener> {
        private final ArgumentResolverService argumentResolverService;
        private final SqsAsyncClientProvider sqsAsyncClientProvider;

        public MySleepingMessageListenerContainerFactory(final ArgumentResolverService argumentResolverService,
                                                         final SqsAsyncClientProvider sqsAsyncClientProvider) {
            this.argumentResolverService = argumentResolverService;
            this.sqsAsyncClientProvider = sqsAsyncClientProvider;
        }

        @Override
        protected Class<SleepingQueueListener> getAnnotationClass() {
            return SleepingQueueListener.class;
        }

        @Override
        protected MessageListenerContainer wrapMethodContainingAnnotation(final Object bean, final Method method, final SleepingQueueListener annotation) {
            // build your container here
            return new CoreMessageListenerContainer(
                   // components in here
            );
        }
    }
    ```

1. Add this bean into the application context by annotating it with the `@Component` annotation or by defining it as a spring bean in a `@Configuration` class.

     ```java
     @Configuration
     public class MyConfiguration {
        @Bean
        public MessageListenerContainerFactory mySleepingMessageListenerContainerFactory(final ArgumentResolverService argumentResolverService) {
            return new MySleepingMessageListenerContainerFactory(argumentResolverService);
        }
     }
     ```

To see how the containers get built, take a look at the
[Spring Core implementations](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/container).
