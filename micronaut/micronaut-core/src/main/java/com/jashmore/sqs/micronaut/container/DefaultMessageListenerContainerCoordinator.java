package com.jashmore.sqs.micronaut.container;

import com.jashmore.documentation.annotations.GuardedBy;
import com.jashmore.documentation.annotations.Nonnull;
import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.documentation.annotations.VisibleForTesting;
import com.jashmore.sqs.container.MessageListenerContainer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.runtime.event.annotation.EventListener;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Default implementation that will build the {@link MessageListenerContainer}s on post construction.
 *
 * <p>This implementation is kept thread safe by synchronizing all access methods. As starting and stopping containers should not be frequent this
 * synchronization method should be fine for now.
 */
@Slf4j
@ThreadSafe
@Context
public class DefaultMessageListenerContainerCoordinator implements MessageListenerContainerCoordinator {

    private final DefaultMessageListenerContainerCoordinatorProperties properties;
    /**
     * These {@link MessageListenerContainerFactory}s should be injected by the spring application and therefore to add more wrappers into the
     * system a corresponding bean with this interface must be included in the application.
     */
    private final List<MessageListenerContainerFactory> messageListenerContainerFactories;

    /**
     * This contains a supplier that can obtain all of the {@link MessageListenerContainer}s that have been built for this application.
     *
     * <p>This must be contained within a {@link Supplier} because the {@link MessageListenerContainerCoordinator} can be dependency injected into the
     * application and because the construction of these containers needs to look at all beans there can be a cyclic dependency.
     */
    @GuardedBy("this")
    private volatile Map<String, MessageListenerContainer> containersLazilyLoadedCache;

    @GuardedBy("this")
    private volatile ApplicationContext applicationContext = null;

    /**
     * Determines whether this container service is currently running in the Spring lifecycle.
     */
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public DefaultMessageListenerContainerCoordinator(
        final DefaultMessageListenerContainerCoordinatorProperties properties,
        final List<MessageListenerContainerFactory> messageListenerContainerFactories,
        final ApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.messageListenerContainerFactories = messageListenerContainerFactories;
        this.containersLazilyLoadedCache = null;
        this.applicationContext = applicationContext;
    }

    @Override
    public Set<MessageListenerContainer> getContainers() {
        return Set.copyOf(getContainerMap().values());
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
            .ofNullable(getContainerMap().get(containerIdentifier))
            .orElseThrow(() -> new IllegalArgumentException("No container with the provided identifier"));

        containerConsumer.accept(container);
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
     * Get the map of container ID to containers.
     *
     * @return the container map
     * @see <a href="https://wiki.sei.cmu.edu/confluence/display/java/LCK10-J.+Use+a+correct+form+of+the+double-checked+locking+idiom#LCK10-J.Useacorrectformofthedouble-checkedlockingidiom-CompliantSolution(Immutable)">Double Check Locks</a>
     *    for where this implementation was gotten from.
     */
    private Map<String, MessageListenerContainer> getContainerMap() {
        Map<String, MessageListenerContainer> localContainer = containersLazilyLoadedCache;
        if (localContainer == null) {
            synchronized (this) {
                localContainer = containersLazilyLoadedCache;
                if (localContainer == null) {
                    localContainer = calculateMessageListenerContainers(messageListenerContainerFactories, applicationContext);
                    containersLazilyLoadedCache = localContainer;
                }
                return localContainer;
            }
        }

        return localContainer;
    }

    /**
     * Initialise all of the containers for this application by finding all bean methods that need to be wrapped.
     */
    private static Map<String, MessageListenerContainer> calculateMessageListenerContainers(
        @Nonnull final List<MessageListenerContainerFactory> messageListenerContainerFactories,
        @Nonnull final ApplicationContext applicationContext
    ) {
        if (messageListenerContainerFactories.isEmpty()) {
            return Collections.emptyMap();
        }

        log.debug("Starting all MessageListenerContainers");
        final Map<String, MessageListenerContainer> messageContainers = new HashMap<>();

        for (final BeanDefinition<?> beanDef : applicationContext.getAllBeanDefinitions()) {
            for (final Method method : beanDef.getBeanType().getMethods()) {
                for (final MessageListenerContainerFactory factory : messageListenerContainerFactories) {
                    if (factory.canHandleMethod(method)) {
                        final Object bean = applicationContext.getBean(beanDef);
                        final MessageListenerContainer messageListenerContainer = factory.buildContainer(bean, method);
                        if (messageContainers.containsKey(messageListenerContainer.getIdentifier())) {
                            throw new IllegalStateException(
                                "Created two MessageListenerContainers with the same identifier: " +
                                messageListenerContainer.getIdentifier()
                            );
                        }
                        log.debug("Created MessageListenerContainer with id: {}", messageListenerContainer.getIdentifier());
                        messageContainers.put(messageListenerContainer.getIdentifier(), messageListenerContainer);
                    }
                }
            }
        }

        return Collections.unmodifiableMap(messageContainers);
    }
}
