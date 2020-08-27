# Spring - How to prevent Message Listener Containers starting on startup

By default, a [DefaultMessageListenerCoordinator](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/DefaultMessageListenerContainerCoordinator.java)
is configured to discover containers as well as start and stop the containers in the Spring Lifecycle. This will auto startup the containers by default but,
if this is not desirable, you can supply your own properties to disable this functionality.

## Steps

1.  Define your own
    [DefaultMessageListenerCoordinatorProperties](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/container/DefaultMessageListenerContainerCoordinatorProperties.java)
    with the configuration you desire.

    ```java
    @Configuration
    class MyConfiguration {

        @Bean
        DefaultMessageListenerCoordinatorProperties defaultMessageListenerCoordinatorProperties() {
            return StaticDefaultMessageListenerContainerCoordinatorProperties.builder().isAutoStartContainersEnabled(false).build();
        }
    }

    ```

This will not work if you have supplied your
own [MessageListenerContainerCoordinator](../../../spring/spring-api/src/main/java/com/jashmore/sqs/spring/container/MessageListenerContainerCoordinator.java)
bean as the default coordinator will now not be configured for you anymore.
