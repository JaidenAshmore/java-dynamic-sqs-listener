# Spring - How to prevent Message Listener Containers starting on startup

By default, a [SpringMessageListenerContainerCoordinator.java](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/SpringMessageListenerContainerCoordinator.java)
is configured to discover containers as well as start and stop the containers in the Spring Lifecycle. This will auto startup the containers by default but,
if this is not desirable, you can supply your own properties to disable this functionality.

## Steps

1.  Define your own
    [SpringMessageListenerContainerCoordinatorProperties.java](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/SpringMessageListenerContainerCoordinatorProperties.java)
    with the configuration you desire.

    ```java
    @Configuration
    class MyConfiguration {

        @Bean
        SpringMessageListenerContainerCoordinatorProperties springMessageListenerContainerCoordinatorProperties() {
            return StaticSpringMessageListenerContainerCoordinatorProperties.builder().isAutoStartContainersEnabled(false).build();
        }
    }

    ```

This will not work if you have supplied your
own [MessageListenerContainerCoordinator](../../../api/src/main/java/com/jashmore/sqs/container/MessageListenerContainerCoordinator.java)
bean as the default coordinator will now not be configured for you anymore.
