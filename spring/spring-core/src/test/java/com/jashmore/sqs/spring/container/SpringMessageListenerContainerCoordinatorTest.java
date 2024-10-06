package com.jashmore.sqs.spring.container;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainerFactory;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@Slf4j
@ExtendWith(MockitoExtension.class)
class SpringMessageListenerContainerCoordinatorTest {

    @Mock
    private SpringMessageListenerContainerCoordinatorProperties properties;

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void whenNoMessageListenerContainerFactoriesPresentBeansAreNotProcessed() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, emptyList());

        // act
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        assertThat(springMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, never()).getBeanDefinitionNames();
    }

    @Test
    void settingApplicationContextTwiceDoesNothing() {
        // arrange
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, Collections.singletonList(messageListenerContainerFactory));
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {});

        // act
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        assertThat(springMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, times(1)).getBeanDefinitionNames();
    }

    @Test
    void buildsMessageListenContainersForEachEligibleBeanMethod() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, Collections.singletonList(messageListenerContainerFactory));
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(any(Object.class), any(Method.class))).thenReturn(Optional.empty());
        when(messageListenerContainerFactory.buildContainer(eq(bean), eq(method))).thenReturn(Optional.of(container));
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);

        // act
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        assertThat(springMessageListenerContainerCoordinator.getContainers()).containsOnly(container);
    }

    @Test
    void methodsThatAreNotEligibleForWrappingWillNotCreateMessageListeners() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, Collections.singletonList(messageListenerContainerFactory));
        when(messageListenerContainerFactory.buildContainer(any(Object.class), any(Method.class))).thenReturn(Optional.empty());
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);

        // act
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        verify(messageListenerContainerFactory, never()).buildContainer(bean, method);
        assertThat(springMessageListenerContainerCoordinator.getContainers()).isEmpty();
    }

    @Test
    void duplicateMessageListenerContainsThrowsExceptionWhenStarting() throws NoSuchMethodException {
        // arrange
        final BeanWithTwoMethods bean = new BeanWithTwoMethods();
        final Method methodOne = bean.getClass().getMethod("methodOne");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, Collections.singletonList(messageListenerContainerFactory));

        final MessageListenerContainer containerOne = mock(MessageListenerContainer.class);
        when(containerOne.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(eq(bean), eq(methodOne))).thenReturn(Optional.of(containerOne));

        final Method methodTwo = bean.getClass().getMethod("methodTwo");
        final MessageListenerContainer containerTwo = mock(MessageListenerContainer.class);
        when(containerTwo.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(eq(bean), eq(methodTwo))).thenReturn(Optional.of(containerTwo));

        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        assertThrows(IllegalStateException.class, springMessageListenerContainerCoordinator::startAllContainers);
    }

    @Test
    void startingContainersWillStartAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, Collections.singletonList(messageListenerContainerFactory));
        when(messageListenerContainerFactory.buildContainer(any(Object.class), any(Method.class))).thenReturn(Optional.empty());
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(eq(bean), eq(method))).thenReturn(Optional.of(container));
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        assertThat(springMessageListenerContainerCoordinator.getContainers()).hasSize(1);

        // act
        springMessageListenerContainerCoordinator.startAllContainers();

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
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, Collections.singletonList(messageListenerContainerFactory));
        when(messageListenerContainerFactory.buildContainer(any(Object.class), any(Method.class))).thenReturn(Optional.empty());
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(eq(bean), eq(method))).thenReturn(Optional.of(container));
        doAnswer(invocationOnMock -> {
                log.info("Stopping container");
                return null;
            })
            .when(container)
            .stop();
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        assertThat(springMessageListenerContainerCoordinator.getContainers()).hasSize(1);

        // act
        springMessageListenerContainerCoordinator.stopAllContainers();
        log.info("Should have stopped all containers");

        // assert
        verify(container).stop();
    }

    @Test
    void stoppingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, emptyList());
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        assertThrows(IllegalArgumentException.class, () -> springMessageListenerContainerCoordinator.stopContainer("unknown"));
    }

    @Test
    void stoppingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, Collections.singletonList(messageListenerContainerFactory));
        when(messageListenerContainerFactory.buildContainer(any(Object.class), any(Method.class))).thenReturn(Optional.empty());
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(eq(bean), eq(method))).thenReturn(Optional.of(container));
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        springMessageListenerContainerCoordinator.stopContainer("identifier");

        // assert
        verify(container).stop();
    }

    @Test
    void startingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, emptyList());
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        assertThrows(IllegalArgumentException.class, () -> springMessageListenerContainerCoordinator.startContainer("unknown"));
    }

    @Test
    void startingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, Collections.singletonList(messageListenerContainerFactory));
        when(messageListenerContainerFactory.buildContainer(any(Object.class), any(Method.class))).thenReturn(Optional.empty());
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(eq(bean), eq(method))).thenReturn(Optional.of(container));
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        springMessageListenerContainerCoordinator.startContainer("identifier");

        // assert
        verify(container).start();
    }

    @Test
    void configuredPropertiesWillDetermineIfContainerIsAutostartup() {
        // arrange
        when(properties.isAutoStartContainersEnabled()).thenReturn(true).thenReturn(false);
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, emptyList());

        // assert
        assertThat(springMessageListenerContainerCoordinator.isAutoStartup()).isTrue();
        assertThat(springMessageListenerContainerCoordinator.isAutoStartup()).isFalse();
    }

    @Test
    void startLifeCycleStartsAllContainers() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator = spy(
            new SpringMessageListenerContainerCoordinator(properties, emptyList())
        );
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        springMessageListenerContainerCoordinator.start();

        // assert
        verify(springMessageListenerContainerCoordinator).startAllContainers();
    }

    @Test
    void stopLifeCycleStopsAllContainers() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator = spy(
            new SpringMessageListenerContainerCoordinator(properties, emptyList())
        );
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        springMessageListenerContainerCoordinator.stop();

        // assert
        verify(springMessageListenerContainerCoordinator).stopAllContainers();
    }

    @Test
    void stopLifeCycleWithCallbackStartsAllContainersAndRunsCallback() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator = spy(
            new SpringMessageListenerContainerCoordinator(properties, emptyList())
        );
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        final Runnable callback = mock(Runnable.class);

        // act
        springMessageListenerContainerCoordinator.stop(callback);

        // assert
        verify(springMessageListenerContainerCoordinator).stop();
        verify(callback).run();
    }

    @Test
    void beanIsNotRunningWhenStartIsNotCalled() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, emptyList());

        // assert
        assertThat(springMessageListenerContainerCoordinator.isRunning()).isFalse();
    }

    @Test
    void startLifeCycleSetsBeanAsRunning() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, emptyList());
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        assertThat(springMessageListenerContainerCoordinator.isRunning()).isFalse();

        // act
        springMessageListenerContainerCoordinator.start();

        // assert
        assertThat(springMessageListenerContainerCoordinator.isRunning()).isTrue();
    }

    @Test
    void stopLifeCycleSetsBeanAsNotRunning() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, emptyList());
        springMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        assertThat(springMessageListenerContainerCoordinator.isRunning()).isFalse();
        springMessageListenerContainerCoordinator.start();

        // act
        springMessageListenerContainerCoordinator.stop();

        // assert
        assertThat(springMessageListenerContainerCoordinator.isRunning()).isFalse();
    }

    @Test
    void beanShouldBeStartedLast() {
        // arrange
        final SpringMessageListenerContainerCoordinator springMessageListenerContainerCoordinator =
            new SpringMessageListenerContainerCoordinator(properties, emptyList());

        // assert
        assertThat(springMessageListenerContainerCoordinator.getPhase()).isEqualTo(Integer.MAX_VALUE);
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
