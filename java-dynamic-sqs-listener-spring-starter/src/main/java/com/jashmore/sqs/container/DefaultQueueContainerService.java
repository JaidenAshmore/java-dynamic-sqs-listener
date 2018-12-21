package com.jashmore.sqs.container;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.jashmore.sqs.QueueWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Default implementation that will build the {@link MessageListenerContainer}s on post construction.
 *
 * <p>This implementation is kept thread safe by synchronizing all access methods. As starting and stopping containers should not be frequent this
 * synchronization method should be fine for now.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ThreadSafe
public class DefaultQueueContainerService implements QueueContainerService, ApplicationContextAware, SmartLifecycle {
    /**
     * Used to be able to start and stop containers concurrently.
     */
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * These {@link QueueWrapper}s should be injected by the spring application and therefore to add more wrappers into the system a corresponding bean
     * with this interface must be included in the application.
     */
    private final List<QueueWrapper> queueWrappers;

    /**
     * This contains all of the containers that have been created from wrapping the Spring Application's bean's methods.
     *
     * <p>This is only modified via the {@link #setApplicationContext(ApplicationContext)} method, which will only be called during the lifecycle of the spring
     * application. This method protects from multiple calls to setting this application context so this will maintain its thread safety.
     */
    @GuardedBy("this")
    private Map<String, MessageListenerContainer> containers = null;

    /**
     * Determines whether this container service is currently running in the Spring lifecycle.
     */
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Initialise all of the containers for this application by finding all bean methods that need to be wrapped.
     */
    @Override
    public synchronized void setApplicationContext(@Nonnull final ApplicationContext applicationContext) throws BeansException {
        if (containers != null) {
            log.warn("Trying to set application context when already set up previously");
            return;
        }

        if (queueWrappers.isEmpty()) {
            containers = ImmutableMap.of();
            return;
        }

        log.debug("Initialising QueueContainerService...");
        final Map<String, MessageListenerContainer> messageContainers = new HashMap<>();

        for (final String beanName : applicationContext.getBeanDefinitionNames()) {
            final Object bean = applicationContext.getBean(beanName);
            for (final Method method : bean.getClass().getMethods()) {
                for (final QueueWrapper annotationProcessor : queueWrappers) {
                    if (annotationProcessor.canWrapMethod(method)) {
                        final MessageListenerContainer messageListenerContainer = annotationProcessor.wrapMethod(bean, method);
                        if (messageContainers.containsKey(messageListenerContainer.getIdentifier())) {
                            throw new IllegalStateException("Created two MessageListenerContainers with the same identifier: "
                                    + messageListenerContainer.getIdentifier());
                        }
                        log.debug("Created MessageListenerContainer with id: {}", messageListenerContainer.getIdentifier());
                        messageContainers.put(messageListenerContainer.getIdentifier(), messageListenerContainer);
                    }
                }
            }
        }

        this.containers = ImmutableMap.copyOf(messageContainers);
    }

    @Override
    public synchronized void startAllContainers() {
        runForAllContainers(MessageListenerContainer::start);
    }

    @Override
    public synchronized void startContainer(final String queueIdentifier) {
        runForQueue(queueIdentifier, MessageListenerContainer::start);
    }

    @Override
    public synchronized void stopAllContainers() {
        runForAllContainers(MessageListenerContainer::stop);
    }

    @Override
    public synchronized void stopContainer(final String queueIdentifier) {
        runForQueue(queueIdentifier, MessageListenerContainer::stop);
    }

    private void runForAllContainers(final Consumer<MessageListenerContainer> containerConsumer) {
        final ExecutorCompletionService<String> executorCompletionService = new ExecutorCompletionService<>(executorService);

        containers.values().forEach(container -> executorCompletionService.submit(() -> containerConsumer.accept(container), null));

        for (int i = 0; i < containers.size(); ++i) {
            try {
                executorCompletionService.take().get();
            } catch (InterruptedException interruptedException) {
                log.info("Thread interrupted while running command across all containers");
                return;
            } catch (ExecutionException executionException) {
                log.error("Error running command on container", executionException);
            }
        }
    }

    private void runForQueue(final String queueIdentifier, Consumer<MessageListenerContainer> runnable) {
        final MessageListenerContainer container = Optional.ofNullable(containers.get(queueIdentifier))
                .orElseThrow(() -> new IllegalArgumentException("No container with the provided identifier"));

        runnable.accept(container);
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
        return ImmutableSet.copyOf(containers.values());
    }
}
