# Spring - How to add Custom MessageProcessingDecorators

## Global [MessageProcessingDecorator](../../../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java)

When you add a [MessageProcessingDecorator](../../../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java) as a bean to the
Spring application, it will automatically be attached to all the core message listeners.

```java
@Configuration
public class MyConfiguration {

    @Bean
    public MessageProcessingDecorator myDecorator() {
        return new MyMessageProcessingDecorator();
    }
}

```

## Per Message Listener decorators

It may not be desirable to have a [MessageProcessingDecorator](../../../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java) attached
to every listener and instead you want to apply it to only a subset of listeners. This can be achieved implementing a
[MessageProcessingDecoratorFactory](../../../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecoratorFactory.java) which
will have the opportunity to wrap a message listener, e.g. by looking for an annotation.

An example decorator which will wrap any message listener that has the `MyAnnotation` annotation.

```java
public class MyDecoratorFactory implements MessageProcessingDecoratorFactory<MyDecorator> {

    @Override
    public Optional<MyDecorator> buildDecorator(
        SqsAsyncClient sqsAsyncClient,
        QueueProperties queueProperties,
        String identifier,
        Object bean,
        Method method
    ) {
        return AnnotationUtils
            .findMethodAnnotation(method, MyAnnotation.class)
            .map(annotation -> new MyDecoratorProperties(annotation.value()))
            .map(properties -> new MyDecorator(properties));
    }
}

```

you then need to provide the factory as a bean in the application:

```java
@Configuration
class MyConfiguration {

    @Bean
    MyDecoratorFactory myDecoratorFactory() {
        return new MyDecoratorFactory();
    }
}

```
