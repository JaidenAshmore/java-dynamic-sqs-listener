package com.jashmore.sqs.spring;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.jashmore.sqs.container.MessageListenerContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Default implementation that will build the {@link MessageListenerContainer}s on post construction.
 *
 * <p>This implementation is kept thread safe by synchronizing all access methods. As starting and stopping containers should not be frequent this
 * synchronization method should be fine for now.
 */
@Slf4j
@ThreadSafe
public class DefaultQueueContainerService implements QueueContainerService, ApplicationContextAware, SmartLifecycle {
    /**
     * These {@link QueueWrapper}s should be injected by the spring application and therefore to add more wrappers into the system a corresponding bean
     * with this interface must be included in the application.
     */
    private final List<QueueWrapper> queueWrappers;

    /**
     * This contains a supplier that can obtain all of the {@link MessageListenerContainer}s that have been built for this application.
     *
     * <p>This must be contained within a {@link Supplier} because the {@link QueueContainerService} can be dependency injected into the application and
     * because the construction of these containers needs to look at all beans there can be a cyclic dependency.
     */
    private Supplier<Map<String, MessageListenerContainer>> containersLazilyLoaded;

    /**
     * Determines whether this container service is currently running in the Spring lifecycle.
     */
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    public DefaultQueueContainerService(final List<QueueWrapper> queueWrappers) {
        this.queueWrappers = queueWrappers;
    }

    @Override
    public void setApplicationContext(@Nonnull final ApplicationContext applicationContext) throws BeansException {
        containersLazilyLoaded = Suppliers.memoize(() -> calculateMessageListenerContainers(queueWrappers, applicationContext));
    }

    @Override
    public void startAllContainers() {
        runForAllContainers(MessageListenerContainer::start);
    }

    @Override
    public void startContainer(final String queueIdentifier) {
        runForQueue(queueIdentifier, MessageListenerContainer::start);
    }

    @Override
    public void stopAllContainers() {
        runForAllContainers(MessageListenerContainer::stop);
    }

    @Override
    public void stopContainer(final String queueIdentifier) {
        runForQueue(queueIdentifier, MessageListenerContainer::stop);
    }

    /**
     * For each of the containers run the following {@link Consumer} asynchronously and wait for them all to finish.
     *
     * @param containerConsumer the consumer to call
     */
    private void runForAllContainers(final Consumer<MessageListenerContainer> containerConsumer) {
        final CompletableFuture<?>[] allTaskCompletableFutures = containersLazilyLoaded.get().values().stream()
                .map(container -> CompletableFuture.runAsync(() -> containerConsumer.accept(container)))
                .toArray(CompletableFuture[]::new);

        try {
            CompletableFuture.allOf(allTaskCompletableFutures).get();
        } catch (InterruptedException interruptedException) {
            log.warn("Thread interrupted while running command across all containers");
        } catch (ExecutionException executionException) {
            log.error("Error running command on container", executionException);
        }
    }

    /**
     * For the given queue with the identifier run the following {@link Consumer} for the container and wait until it is finished.
     *
     * @param queueIdentifier   the identifier of the queue
     * @param containerConsumer the container consumer to run
     */
    private void runForQueue(final String queueIdentifier, final Consumer<MessageListenerContainer> containerConsumer) {
        final MessageListenerContainer container = Optional.ofNullable(containersLazilyLoaded.get().get(queueIdentifier))
                .orElseThrow(() -> new IllegalArgumentException("No container with the provided identifier"));

        containerConsumer.accept(container);
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public synchronized void start() {
        log.info("Starting service");

        startAllContainers();

        isRunning.set(true);
    }

    @Override
    public synchronized void stop() {
        log.info("Stopping service");

        stopAllContainers();

        isRunning.set(false);
    }

    @Override
    public synchronized void stop(@Nonnull final Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public synchronized boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public synchronized int getPhase() {
        return Integer.MAX_VALUE;
    }

    @VisibleForTesting
    synchronized Set<MessageListenerContainer> getContainers() {
        return ImmutableSet.copyOf(containersLazilyLoaded.get().values());
    }

    /**
     * Initialise all of the containers for this application by finding all bean methods that need to be wrapped.
     */
    private static Map<String, MessageListenerContainer> calculateMessageListenerContainers(
            @Nonnull final List<QueueWrapper> queueWrappers,
            @Nonnull final ApplicationContext applicationContext) {
        if (queueWrappers.isEmpty()) {
            return ImmutableMap.of();
        }

        log.debug("Initialising QueueContainerService...");
        final Map<String, MessageListenerContainer> messageContainers = new HashMap<>();

        for (final String beanName : applicationContext.getBeanDefinitionNames()) {
            final Object bean = applicationContext.getBean(beanName);
            for (final Method method : bean.getClass().getMethods()) {
                for (final QueueWrapper annotationProcessor : queueWrappers) {
                    if (annotationProcessor.canWrapMethod(method)) {
                        final IdentifiableMessageListenerContainer identifiableMessageListenerContainer = annotationProcessor.wrapMethod(bean, method);
                        if (messageContainers.containsKey(identifiableMessageListenerContainer.getIdentifier())) {
                            throw new IllegalStateException("Created two MessageListenerContainers with the same identifier: "
                                    + identifiableMessageListenerContainer.getIdentifier());
                        }
                        log.debug("Created MessageListenerContainer with id: {}", identifiableMessageListenerContainer.getIdentifier());
                        messageContainers.put(identifiableMessageListenerContainer.getIdentifier(), identifiableMessageListenerContainer.getContainer());
                    }
                }
            }
        }

        return ImmutableMap.copyOf(messageContainers);
    }
}
