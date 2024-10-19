package com.jashmore.sqs.micronaut.container;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.annotations.core.fifo.FifoQueueListener;
import com.jashmore.sqs.annotations.core.prefetch.PrefetchingQueueListener;
import com.jashmore.sqs.container.MessageListenerContainerFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementations (nested here) will process a listener-annotated method by registering an appropriate
 * {@link com.jashmore.sqs.container.MessageListenerContainer}.
 *
 * <p>This relies upon annotation processing provided by the
 * {@code java-dynamic-sqs-listener-micronaut-inject-java} package, which transforms the values of annotations
 * from {@link com.jashmore.sqs.annotations} by adding the stereotype {@link io.micronaut.context.annotation.Executable}
 * at compile time, so that these {@link ExecutableMethodProcessor} can receive them.
 *
 * @param <T> an annotation from {@link com.jashmore.sqs.annotations}
 */
@Slf4j
public abstract class MicronautListenerMethodProcessor<T extends Annotation> implements ExecutableMethodProcessor<T> {

    private final MessageListenerContainerFactory factory;
    private final ApplicationContext applicationContext;
    private final MicronautMessageListenerContainerRegistry containerRegistry;

    protected MicronautListenerMethodProcessor(
        MessageListenerContainerFactory messageListenerContainerFactory,
        ApplicationContext applicationContext,
        MicronautMessageListenerContainerRegistry containerRegistry
    ) {
        this.factory = messageListenerContainerFactory;
        this.applicationContext = applicationContext;
        this.containerRegistry = containerRegistry;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> executableMethod) {
        Object bean = applicationContext.getBean(beanDefinition);
        factory.buildContainer(bean, executableMethod.getTargetMethod()).ifPresent(containerRegistry::put);
    }

    /*
     * Implementations
     */

    @Singleton
    public static class MicronautQueueListenerMethodProcessor extends MicronautListenerMethodProcessor<QueueListener> {

        public MicronautQueueListenerMethodProcessor(
            @Named("queueListener") MessageListenerContainerFactory messageListenerContainerFactory,
            ApplicationContext applicationContext,
            MicronautMessageListenerContainerRegistry containerRegistry
        ) {
            super(messageListenerContainerFactory, applicationContext, containerRegistry);
        }
    }

    @Singleton
    public static class MicronautFifoQueueListenerMethodProcessor extends MicronautListenerMethodProcessor<FifoQueueListener> {

        public MicronautFifoQueueListenerMethodProcessor(
            @Named("fifoQueueListener") MessageListenerContainerFactory messageListenerContainerFactory,
            ApplicationContext applicationContext,
            MicronautMessageListenerContainerRegistry containerRegistry
        ) {
            super(messageListenerContainerFactory, applicationContext, containerRegistry);
        }
    }

    @Singleton
    public static class MicronautPrefetchingQueueListenerMethodProcessor
        extends MicronautListenerMethodProcessor<PrefetchingQueueListener> {

        public MicronautPrefetchingQueueListenerMethodProcessor(
            @Named("prefetchingQueueListener") MessageListenerContainerFactory messageListenerContainerFactory,
            ApplicationContext applicationContext,
            MicronautMessageListenerContainerRegistry containerRegistry
        ) {
            super(messageListenerContainerFactory, applicationContext, containerRegistry);
        }
    }
}
