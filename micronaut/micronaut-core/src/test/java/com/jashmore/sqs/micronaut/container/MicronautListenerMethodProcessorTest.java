package com.jashmore.sqs.micronaut.container;

import static org.mockito.Mockito.*;

import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainerFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import java.lang.reflect.Method;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({ "rawtypes", "unchecked" })
@Slf4j
@ExtendWith(MockitoExtension.class)
public class MicronautListenerMethodProcessorTest {

    @Mock
    MessageListenerContainerFactory factory;

    @Mock
    ApplicationContext applicationContext;

    @Mock
    MicronautMessageListenerContainerRegistry containerRegistry;

    @Nested
    class Basic {

        @Test
        void processCannotBuildContainer() {
            MicronautListenerMethodProcessor<?> processor = new MicronautListenerMethodProcessor.MicronautQueueListenerMethodProcessor(
                factory,
                applicationContext,
                containerRegistry
            );
            BeanDefinition beanDefinition = mock(BeanDefinition.class);
            Object bean = mock(Object.class);
            when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
            ExecutableMethod executableMethod = mock(ExecutableMethod.class);
            Method method = mock(Method.class);
            when(executableMethod.getTargetMethod()).thenReturn(method);
            when(factory.buildContainer(bean, method)).thenReturn(Optional.empty());

            processor.process(beanDefinition, executableMethod);

            verify(containerRegistry, never()).put(any());
        }

        @Test
        void processBuildsContainer() {
            MicronautListenerMethodProcessor<?> processor = new MicronautListenerMethodProcessor.MicronautQueueListenerMethodProcessor(
                factory,
                applicationContext,
                containerRegistry
            );
            BeanDefinition beanDefinition = mock(BeanDefinition.class);
            Object bean = mock(Object.class);
            when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
            ExecutableMethod executableMethod = mock(ExecutableMethod.class);
            Method method = mock(Method.class);
            when(executableMethod.getTargetMethod()).thenReturn(method);
            MessageListenerContainer container = mock(MessageListenerContainer.class);
            when(factory.buildContainer(bean, method)).thenReturn(Optional.of(container));

            processor.process(beanDefinition, executableMethod);

            verify(containerRegistry).put(container);
        }
    }

    @Nested
    class Fifo {

        @Test
        void processCannotBuildContainer() {
            MicronautListenerMethodProcessor<?> processor = new MicronautListenerMethodProcessor.MicronautFifoQueueListenerMethodProcessor(
                factory,
                applicationContext,
                containerRegistry
            );
            BeanDefinition beanDefinition = mock(BeanDefinition.class);
            Object bean = mock(Object.class);
            when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
            ExecutableMethod executableMethod = mock(ExecutableMethod.class);
            Method method = mock(Method.class);
            when(executableMethod.getTargetMethod()).thenReturn(method);
            when(factory.buildContainer(bean, method)).thenReturn(Optional.empty());

            processor.process(beanDefinition, executableMethod);

            verify(containerRegistry, never()).put(any());
        }

        @Test
        void processBuildsContainer() {
            MicronautListenerMethodProcessor<?> processor = new MicronautListenerMethodProcessor.MicronautFifoQueueListenerMethodProcessor(
                factory,
                applicationContext,
                containerRegistry
            );
            BeanDefinition beanDefinition = mock(BeanDefinition.class);
            Object bean = mock(Object.class);
            when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
            ExecutableMethod executableMethod = mock(ExecutableMethod.class);
            Method method = mock(Method.class);
            when(executableMethod.getTargetMethod()).thenReturn(method);
            MessageListenerContainer container = mock(MessageListenerContainer.class);
            when(factory.buildContainer(bean, method)).thenReturn(Optional.of(container));

            processor.process(beanDefinition, executableMethod);

            verify(containerRegistry).put(container);
        }
    }

    @Nested
    class Prefetching {

        @Test
        void processCannotBuildContainer() {
            MicronautListenerMethodProcessor<?> processor =
                new MicronautListenerMethodProcessor.MicronautPrefetchingQueueListenerMethodProcessor(
                    factory,
                    applicationContext,
                    containerRegistry
                );
            BeanDefinition beanDefinition = mock(BeanDefinition.class);
            Object bean = mock(Object.class);
            when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
            ExecutableMethod executableMethod = mock(ExecutableMethod.class);
            Method method = mock(Method.class);
            when(executableMethod.getTargetMethod()).thenReturn(method);
            when(factory.buildContainer(bean, method)).thenReturn(Optional.empty());

            processor.process(beanDefinition, executableMethod);

            verify(containerRegistry, never()).put(any());
        }

        @Test
        void processBuildsContainer() {
            MicronautListenerMethodProcessor<?> processor =
                new MicronautListenerMethodProcessor.MicronautPrefetchingQueueListenerMethodProcessor(
                    factory,
                    applicationContext,
                    containerRegistry
                );
            BeanDefinition beanDefinition = mock(BeanDefinition.class);
            Object bean = mock(Object.class);
            when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
            ExecutableMethod executableMethod = mock(ExecutableMethod.class);
            Method method = mock(Method.class);
            when(executableMethod.getTargetMethod()).thenReturn(method);
            MessageListenerContainer container = mock(MessageListenerContainer.class);
            when(factory.buildContainer(bean, method)).thenReturn(Optional.of(container));

            processor.process(beanDefinition, executableMethod);

            verify(containerRegistry).put(container);
        }
    }
}
