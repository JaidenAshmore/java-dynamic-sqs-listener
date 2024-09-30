package com.jashmore.sqs.micronaut.container;

import com.jashmore.sqs.container.MessageListenerContainer;

import java.util.Set;

/**
 * Service that can be injected into the Spring Application to start and stop the containers that are controlling
 * the consumption of queue messages.
 */
public interface MessageListenerContainerCoordinator {
    /**
     * Get all of the containers that have been configured with this coordinator.
     *
     * @return the set of containers
     */
    Set<MessageListenerContainer> getContainers();

    /**
     * Start all of the containers that have been built for the application.
     */
    void startAllContainers();

    /**
     * Start a container with the given identifier.
     *
     * @param queueIdentifier the identifier for the queue
     * @throws IllegalArgumentException if there is not a container with the given identifier
     */
    void startContainer(String queueIdentifier);

    /**
     * Stop all of the containers that have been built for the application.
     */
    void stopAllContainers();

    /**
     * Stop a container with the given identifier.
     *
     * @param queueIdentifier the identifier for the queue
     * @throws IllegalArgumentException if there is not a container with the given identifier
     */
    void stopContainer(String queueIdentifier);
}
