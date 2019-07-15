# Spring - How to start and stop Queue Listeners
Queue Listeners can be started and stopped while it is executing via the
[MessageListenerContainerCoordinator](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-api/src/main/java/com/jashmore/sqs/spring/container/MessageListenerContainerCoordinator.java).
To choose a specific queue the unique identifier for the listener must be supplied which was either set on the listener specifically or if none were supplied
an automatic is supplied.

## Setting a Queue Listener's identifier
 
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
When no identifier is supplied, a default will be generated for you, for example in the scenario below the identifier built will be `my-class-message-handler`.

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
