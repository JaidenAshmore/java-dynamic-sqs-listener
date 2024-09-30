package com.jashmore.sqs.micronaut.container;

import com.jashmore.sqs.container.MessageListenerContainer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class DefaultMessageListenerContainerCoordinatorTest {

    @Mock
    private DefaultMessageListenerContainerCoordinatorProperties properties;

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void whenNoMessageListenerContainerFactoriesPresentBeansAreNotProcessed() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
            new DefaultMessageListenerContainerCoordinator(properties, emptyList(), applicationContext);

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, never()).getAllBeanDefinitions();
    }

    @Test
    void settingApplicationContextTwiceDoesNothing() {
        // arrange
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
            new DefaultMessageListenerContainerCoordinator(
                    properties,
                    Collections.singletonList(messageListenerContainerFactory),
                    applicationContext);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(emptyList());

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, times(1)).getAllBeanDefinitions();
    }

    @Test
    void buildsMessageListenContainersForEachEligibleBeanMethod() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
                new DefaultMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).containsOnly(container);
    }

    @Test
    void methodsThatAreNotEligibleForWrappingWillNotCreateMessageListeners() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
                new DefaultMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // assert
        verify(messageListenerContainerFactory, never()).buildContainer(bean, method);
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).isEmpty();
    }

    @Test
    void duplicateMessageListenerContainsThrowsExceptionWhenStarting() throws NoSuchMethodException {
        // arrange
        final BeanWithTwoMethods bean = new BeanWithTwoMethods();
        final Method methodOne = bean.getClass().getMethod("methodOne");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);

        when(messageListenerContainerFactory.canHandleMethod(methodOne)).thenReturn(true);
        final MessageListenerContainer containerOne = mock(MessageListenerContainer.class);
        when(containerOne.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, methodOne)).thenReturn(containerOne);

        final Method methodTwo = bean.getClass().getMethod("methodTwo");
        when(messageListenerContainerFactory.canHandleMethod(methodTwo)).thenReturn(true);
        final MessageListenerContainer containerTwo = mock(MessageListenerContainer.class);
        when(containerTwo.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, methodTwo)).thenReturn(containerTwo);

        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(BeanWithTwoMethods.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
                new DefaultMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // act
        assertThrows(IllegalStateException.class, defaultMessageListenerContainerCoordinator::startAllContainers);
    }

    @Test
    void startingContainersWillStartAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
                new DefaultMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).hasSize(1);

        // act
        defaultMessageListenerContainerCoordinator.startAllContainers();

        // assert
        verify(container).start();
    }

    @Test
    void stoppingAllContainersWillStopAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        log.debug("Starting stoppingAllContainersWillStopAllMessageListenerContainersBuilt");
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        doAnswer(invocationOnMock -> {
            log.info("Stopping container");
            return null;
        })
            .when(container)
            .stop();
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
                new DefaultMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).hasSize(1);

        // act
        defaultMessageListenerContainerCoordinator.stopAllContainers();
        log.info("Should have stopped all containers");

        // assert
        verify(container).stop();
    }

    @Test
    void stoppingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
            new DefaultMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);

        // act
        assertThrows(IllegalArgumentException.class, () -> defaultMessageListenerContainerCoordinator.stopContainer("unknown"));
    }

    @Test
    void stoppingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
                new DefaultMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.stopContainer("identifier");

        // assert
        verify(container).stop();
    }

    @Test
    void startingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
            new DefaultMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);

        // act
        assertThrows(IllegalArgumentException.class, () -> defaultMessageListenerContainerCoordinator.startContainer("unknown"));
    }

    @Test
    void startingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
                new DefaultMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.startContainer("identifier");

        // assert
        verify(container).start();
    }

    @Test
    void startLifeCycleStartsAllContainers() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = spy(
            new DefaultMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext)
        );

        // act
        defaultMessageListenerContainerCoordinator.start();

        // assert
        verify(defaultMessageListenerContainerCoordinator).startAllContainers();
    }

    @Test
    void stopLifeCycleStopsAllContainers() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = spy(
            new DefaultMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext)
        );

        // act
        defaultMessageListenerContainerCoordinator.stop();

        // assert
        verify(defaultMessageListenerContainerCoordinator).stopAllContainers();
    }

    @Test
    void beanIsNotRunningWhenStartIsNotCalled() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
            new DefaultMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();
    }

    @Test
    void startLifeCycleSetsBeanAsRunning() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
            new DefaultMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();

        // act
        defaultMessageListenerContainerCoordinator.start();

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isTrue();
    }

    @Test
    void stopLifeCycleSetsBeanAsNotRunning() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator =
            new DefaultMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();
        defaultMessageListenerContainerCoordinator.start();

        // act
        defaultMessageListenerContainerCoordinator.stop();

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();
    }

    @SuppressWarnings("WeakerAccess")
    public static class Bean {

        public void method() {}
    }

    @SuppressWarnings("WeakerAccess")
    public static class BeanWithTwoMethods {

        public void methodOne() {}

        public void methodTwo() {}
    }
}
