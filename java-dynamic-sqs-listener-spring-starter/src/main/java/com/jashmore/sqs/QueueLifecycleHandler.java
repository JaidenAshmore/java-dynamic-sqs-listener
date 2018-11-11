package com.jashmore.sqs;

import static java.util.stream.Collectors.toList;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Handles the lifecycle for the queue listeners by determine which methods need to be wrapped, wrap and start a container on application starting and
 * stopping the container when the {@link org.springframework.boot.SpringApplication} is shut down.
 */
@Slf4j
@Component
public class QueueLifecycleHandler implements Lifecycle {
    private final ApplicationContext applicationContext;
    private final BeanFactory beanFactory;
    private final List<QueueAnnotationProcessor> annotationProcessors;
    private final List<MessageListenerContainer> messageListenerContainers;
    private final AtomicBoolean isRunning;

    @Autowired
    public QueueLifecycleHandler(
            final ApplicationContext applicationContext,
            final BeanFactory beanFactory,
            final List<QueueAnnotationProcessor> annotationProcessors) {
        this.applicationContext = applicationContext;
        this.beanFactory = beanFactory;
        this.annotationProcessors = annotationProcessors;
        this.messageListenerContainers = new ArrayList<>();
        this.isRunning = new AtomicBoolean(false);
    }

    @PostConstruct
    @Override
    public synchronized void start() {
        log.info("Starting queue lifecycle handler");
        if (isRunning.get()) {
            return;
        }

        for (final String beanName : applicationContext.getBeanDefinitionNames()) {
            final Object bean = beanFactory.getBean(beanName);
            for (final Method method : bean.getClass().getMethods()) {
                for (final QueueAnnotationProcessor annotationProcessor: annotationProcessors) {
                    if (annotationProcessor.canHandleMethod(method)) {
                        final MessageListenerContainer messageListenerContainer = annotationProcessor.wrapMethod(bean, method);
                        messageListenerContainers.add(messageListenerContainer);
                    }
                }
            }
        }

        for (final MessageListenerContainer container: messageListenerContainers) {
            container.start();
        }

        isRunning.set(true);
    }

    @PreDestroy
    @Override
    public synchronized void stop() {
        log.info("Stopping queue lifecycle handler");
        if (!isRunning.get()) {
            return;
        }

        try {
            final List<? extends Future<?>> futures = messageListenerContainers.stream()
                    .map(MessageListenerContainer::stop)
                    .collect(toList());
            for (final Future<?> future : futures) {
                try {
                    future.get();
                } catch (final InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final ExecutionException executionException) {
                    throw new RuntimeException(executionException);
                }
            }
        } finally {
            messageListenerContainers.clear();
            isRunning.set(false);
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }
}
