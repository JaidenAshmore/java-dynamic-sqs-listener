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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
    private final List<QueueWrapper> queueWrappers;

    @GuardedBy("this")
    private Map<String, MessageListenerContainer> containers = null;

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
        containers.values().stream().parallel()
                .forEach(MessageListenerContainer::start);
    }

    @Override
    public synchronized void startContainer(final String queueIdentifier) {
        runForQueue(queueIdentifier, MessageListenerContainer::start);
    }

    @Override
    public synchronized void stopAllContainers() {
        containers.values().stream().parallel()
                .forEach(MessageListenerContainer::stop);
    }

    @Override
    public synchronized void stopContainer(final String queueIdentifier) {
        runForQueue(queueIdentifier, MessageListenerContainer::stop);
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
