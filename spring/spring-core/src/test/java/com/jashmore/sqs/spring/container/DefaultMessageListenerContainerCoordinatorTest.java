package com.jashmore.sqs.spring.container;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.container.MessageListenerContainer;
import java.lang.reflect.Method;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@Slf4j
@ExtendWith(MockitoExtension.class)
class DefaultMessageListenerContainerCoordinatorTest {

    @Mock
    private DefaultMessageListenerContainerCoordinatorProperties properties;

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void whenNoMessageListenerContainerFactoriesPresentBeansAreNotProcessed() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            emptyList()
        );

        // act
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, never()).getBeanDefinitionNames();
    }

    @Test
    void settingApplicationContextTwiceDoesNothing() {
        // arrange
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            Collections.singletonList(messageListenerContainerFactory)
        );
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {});

        // act
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, times(1)).getBeanDefinitionNames();
    }

    @Test
    void buildsMessageListenContainersForEachEligibleBeanMethod() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            Collections.singletonList(messageListenerContainerFactory)
        );
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);

        // act
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).containsOnly(container);
    }

    @Test
    void methodsThatAreNotEligibleForWrappingWillNotCreateMessageListeners() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            Collections.singletonList(messageListenerContainerFactory)
        );
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);

        // act
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

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
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            Collections.singletonList(messageListenerContainerFactory)
        );
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

        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        assertThrows(IllegalStateException.class, defaultMessageListenerContainerCoordinator::startAllContainers);
    }

    @Test
    void startingContainersWillStartAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            Collections.singletonList(messageListenerContainerFactory)
        );
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
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
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            Collections.singletonList(messageListenerContainerFactory)
        );
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        doAnswer(
                invocationOnMock -> {
                    log.info("Stopping container");
                    return null;
                }
            )
            .when(container)
            .stop();
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
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
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            emptyList()
        );
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        assertThrows(IllegalArgumentException.class, () -> defaultMessageListenerContainerCoordinator.stopContainer("unknown"));
    }

    @Test
    void stoppingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            Collections.singletonList(messageListenerContainerFactory)
        );
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.stopContainer("identifier");

        // assert
        verify(container).stop();
    }

    @Test
    void startingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            emptyList()
        );
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        assertThrows(IllegalArgumentException.class, () -> defaultMessageListenerContainerCoordinator.startContainer("unknown"));
    }

    @Test
    void startingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            Collections.singletonList(messageListenerContainerFactory)
        );
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.startContainer("identifier");

        // assert
        verify(container).start();
    }

    @Test
    void configuredPropertiesWillDetermineIfContainerIsAutostartup() {
        // arrange
        when(properties.isAutoStartContainersEnabled()).thenReturn(true).thenReturn(false);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            emptyList()
        );

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isAutoStartup()).isTrue();
        assertThat(defaultMessageListenerContainerCoordinator.isAutoStartup()).isFalse();
    }

    @Test
    void startLifeCycleStartsAllContainers() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = spy(
            new DefaultMessageListenerContainerCoordinator(properties, emptyList())
        );
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.start();

        // assert
        verify(defaultMessageListenerContainerCoordinator).startAllContainers();
    }

    @Test
    void stopLifeCycleStopsAllContainers() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = spy(
            new DefaultMessageListenerContainerCoordinator(properties, emptyList())
        );
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.stop();

        // assert
        verify(defaultMessageListenerContainerCoordinator).stopAllContainers();
    }

    @Test
    void stopLifeCycleWithCallbackStartsAllContainersAndRunsCallback() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = spy(
            new DefaultMessageListenerContainerCoordinator(properties, emptyList())
        );
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        final Runnable callback = mock(Runnable.class);

        // act
        defaultMessageListenerContainerCoordinator.stop(callback);

        // assert
        verify(defaultMessageListenerContainerCoordinator).stop();
        verify(callback).run();
    }

    @Test
    void beanIsNotRunningWhenStartIsNotCalled() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            emptyList()
        );

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();
    }

    @Test
    void startLifeCycleSetsBeanAsRunning() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            emptyList()
        );
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();

        // act
        defaultMessageListenerContainerCoordinator.start();

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isTrue();
    }

    @Test
    void stopLifeCycleSetsBeanAsNotRunning() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            emptyList()
        );
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();
        defaultMessageListenerContainerCoordinator.start();

        // act
        defaultMessageListenerContainerCoordinator.stop();

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();
    }

    @Test
    void beanShouldBeStartedLast() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(
            properties,
            emptyList()
        );

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getPhase()).isEqualTo(Integer.MAX_VALUE);
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
