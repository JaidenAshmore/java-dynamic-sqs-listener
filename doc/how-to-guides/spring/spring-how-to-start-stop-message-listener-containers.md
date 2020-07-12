# Spring - How to start and stop Message Listener Containers

[MessageListenerContainer](../../../api/src/main/java/com/jashmore/sqs/container/MessageListenerContainer.java) can be started and stopped while the Spring
application is executing via the
[MessageListenerContainerCoordinator](../../../spring/spring-api/src/main/java/com/jashmore/sqs/spring/container/MessageListenerContainerCoordinator.java).
Each container has a unique identifier and this can be used to indicate which container to start or stop. The core message listeners allow for a custom
identifier to be supplied, otherwise a default will be generated from the class and method name of the message listener.

## Setting a Message Listener's identifier

### Setting it explicitly

The core queue listener annotations provide a field that can be set for the identifier for this queue.

```java
public class MyClass {
    @QueueListener(value="myQueueName", identifier="my-identifier")
    public void messageHandler(@Payload String payload) {

    }
}
```

### Using the default identifier

When the message listener does not set a custom identifier, a default identifier will be constructed from the class and method name. In the following example,
the message listener will automatically set `my-class-message-handler` as the identifier.

```java
public class MyClass {
    @QueueListener(value="myQueueName")
    public void messageHandler(@Payload String payload) {

    }
}
```

## Starting/Stopping the queue

```java
@Service
public class MyService {
    private final MessageListenerContainerCoordinator messageListenerContainerCoordinator;

    @Autowired
    public MyService(final MessageListenerContainerCoordinator messageListenerContainerCoordinator) {
        this.messageListenerContainerCoordinator = messageListenerContainerCoordinator;
    }

    public void someMethod() {
        messageListenerContainerCoordinator.stopContainer("my-identifier");

        Thread.sleep(1000);

        messageListenerContainerCoordinator.startContainer("my-identifier");
    }
}
```

## Starting/Stopping all queues

```java
@Service
public class MyService {
    private final MessageListenerContainerCoordinator messageListenerContainerCoordinator;

    @Autowired
    public MyService(final MessageListenerContainerCoordinator messageListenerContainerCoordinator) {
        this.messageListenerContainerCoordinator = messageListenerContainerCoordinator;
    }

    public void someMethod() {
        messageListenerContainerCoordinator.startAllContainers();

        Thread.sleep(1000);

        messageListenerContainerCoordinator.stopAllContainers();
    }
}
```
