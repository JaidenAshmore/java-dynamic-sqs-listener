package com.jashmore.sqs.micronaut.container;

import com.jashmore.documentation.annotations.VisibleForTesting;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainerCoordinator;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class MicronautMessageListenerContainerCoordinator implements MessageListenerContainerCoordinator {

    private final MicronautMessageListenerContainerCoordinatorProperties properties;
    private final MicronautMessageListenerContainerRegistry containerRegistry;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public MicronautMessageListenerContainerCoordinator(
        final MicronautMessageListenerContainerCoordinatorProperties properties,
        final MicronautMessageListenerContainerRegistry containerRegistry
    ) {
        this.properties = properties;
        this.containerRegistry = containerRegistry;
    }

    @Override
    public Set<MessageListenerContainer> getContainers() {
        return Set.copyOf(containerRegistry.getContainerMap().values());
    }

    @Override
    public void startAllContainers() {
        runForAllContainers(MessageListenerContainer::start);
    }

    @Override
    public void startContainer(final String queueIdentifier) {
        runForContainer(queueIdentifier, MessageListenerContainer::start);
    }

    @Override
    public void stopAllContainers() {
        runForAllContainers(MessageListenerContainer::stop);
    }

    @Override
    public void stopContainer(final String queueIdentifier) {
        runForContainer(queueIdentifier, MessageListenerContainer::stop);
    }

    @VisibleForTesting
    void start() {
        if (!isRunning()) {
            log.info("Starting MessageListenerContainerCoordinator");
            startAllContainers();
            isRunning.set(true);
        } else {
            log.warn("Received StartupEvent but already running");
        }
    }

    @VisibleForTesting
    void stop() {
        log.info("Stopping MessageListenerContainerCoordinator");
        stopAllContainers();
        isRunning.set(false);
    }

    @EventListener
    public void onStartup(StartupEvent startupEvent) {
        if (properties.isAutoStartContainersEnabled()) {
            start();
        }
    }

    @EventListener
    public void onShutdown(ShutdownEvent shutdownEvent) {
        stop();
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * For each of the containers run the following {@link Consumer} asynchronously and wait for them all to finish.
     *
     * @param containerConsumer the consumer to call
     */
    private void runForAllContainers(final Consumer<MessageListenerContainer> containerConsumer) {
        final CompletableFuture<?>[] allTaskCompletableFutures = getContainers()
            .stream()
            .map(container -> CompletableFuture.runAsync(() -> containerConsumer.accept(container)))
            .toArray(CompletableFuture[]::new);

        try {
            CompletableFuture.allOf(allTaskCompletableFutures).get();
        } catch (InterruptedException interruptedException) {
            log.warn("Thread interrupted while running command across all containers");
            Thread.currentThread().interrupt();
        } catch (ExecutionException executionException) {
            log.error("Error running command on container", executionException);
        }
    }

    /**
     * For the given container with identifier run the following {@link Consumer} and wait until it is finished.
     *
     * @param containerIdentifier the identifier of the queue
     * @param containerConsumer   the container consumer to run
     */
    private void runForContainer(final String containerIdentifier, final Consumer<MessageListenerContainer> containerConsumer) {
        final MessageListenerContainer container = Optional
            .ofNullable(containerRegistry.getContainerMap().get(containerIdentifier))
            .orElseThrow(() -> new IllegalArgumentException("No container with the provided identifier"));

        containerConsumer.accept(container);
    }
}
