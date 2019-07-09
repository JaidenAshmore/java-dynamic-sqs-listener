package com.jashmore.sqs.spring.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.container.MessageListenerContainer;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DefaultMessageListenerContainerCoordinatorTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ApplicationContext applicationContext;

    @Test
    public void whenNoMessageListenerContainerFactoriesPresentBeansAreNotProcessed() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of());

        // act
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, never()).getBeanDefinitionNames();
    }

    @Test
    public void settingApplicationContextTwiceDoesNothing() {
        // arrange
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of(messageListenerContainerFactory));
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {});

        // act
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, times(1)).getBeanDefinitionNames();
    }

    @Test
    public void buildsMessageListenContainersForEachEligibleBeanMethod() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of(messageListenerContainerFactory));
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"bean"});
        when(applicationContext.getBean("bean")).thenReturn(bean);

        // act
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).containsOnly(container);
    }

    @Test
    public void methodsThatAreNotEligibleForWrappingWillNotCreateMessageListeners() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of(messageListenerContainerFactory));
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"bean"});
        when(applicationContext.getBean("bean")).thenReturn(bean);

        // act
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // assert
        verify(messageListenerContainerFactory, never()).buildContainer(bean, method);
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).isEmpty();
    }

    @Test
    public void duplicateMessageListenerContainsThrowsExceptionWhenStarting() throws NoSuchMethodException {
        // arrange
        final BeanWithTwoMethods bean = new BeanWithTwoMethods();
        final Method methodOne = bean.getClass().getMethod("methodOne");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of(messageListenerContainerFactory));
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

        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"bean"});
        when(applicationContext.getBean("bean")).thenReturn(bean);
        expectedException.expect(IllegalStateException.class);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.startAllContainers();
    }

    @Test
    public void startingContainersWillStartAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of(messageListenerContainerFactory));
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"bean"});
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        assertThat(defaultMessageListenerContainerCoordinator.getContainers()).hasSize(1);

        // act
        defaultMessageListenerContainerCoordinator.startAllContainers();

        // assert
        verify(container).start();
    }

    @Test
    public void stoppingAllContainersWillStopAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        log.debug("Starting stoppingAllContainersWillStopAllMessageListenerContainersBuilt");
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of(messageListenerContainerFactory));
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        doAnswer((invocationOnMock) -> {
            log.info("Stopping container");
            return null;
        }).when(container).stop();
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"bean"});
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
    public void stoppingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of());
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        expectedException.expect(IllegalArgumentException.class);

        // act
        defaultMessageListenerContainerCoordinator.stopContainer("unknown");
    }

    @Test
    public void stoppingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of(messageListenerContainerFactory));
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"bean"});
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.stopContainer("identifier");

        // assert
        verify(container).stop();
    }

    @Test
    public void startingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of());
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        expectedException.expect(IllegalArgumentException.class);

        // act
        defaultMessageListenerContainerCoordinator.startContainer("unknown");
    }

    @Test
    public void startingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final MessageListenerContainerFactory messageListenerContainerFactory = mock(MessageListenerContainerFactory.class);
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of(messageListenerContainerFactory));
        when(messageListenerContainerFactory.canHandleMethod(any(Method.class))).thenReturn(false);
        when(messageListenerContainerFactory.canHandleMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"bean"});
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.startContainer("identifier");

        // assert
        verify(container).start();
    }

    @Test
    public void autoStartsService() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of());

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isAutoStartup()).isTrue();
    }

    @Test
    public void startLifeCycleStartsAllContainers() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = spy(new DefaultMessageListenerContainerCoordinator(ImmutableList.of()));
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.start();

        // assert
        verify(defaultMessageListenerContainerCoordinator).startAllContainers();
    }

    @Test
    public void stopLifeCycleStopsAllContainers() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = spy(new DefaultMessageListenerContainerCoordinator(ImmutableList.of()));
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);

        // act
        defaultMessageListenerContainerCoordinator.stop();

        // assert
        verify(defaultMessageListenerContainerCoordinator).stopAllContainers();
    }

    @Test
    public void stopLifeCycleWithCallbackStartsAllContainersAndRunsCallback() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = spy(new DefaultMessageListenerContainerCoordinator(ImmutableList.of()));
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        final Runnable callback = mock(Runnable.class);

        // act
        defaultMessageListenerContainerCoordinator.stop(callback);

        // assert
        verify(defaultMessageListenerContainerCoordinator).stop();
        verify(callback).run();
    }

    @Test
    public void beanIsNotRunningWhenStartIsNotCalled() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of());

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();
    }

    @Test
    public void startLifeCycleSetsBeanAsRunning() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of());
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();

        // act
        defaultMessageListenerContainerCoordinator.start();

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isTrue();
    }

    @Test
    public void stopLifeCycleSetsBeanAsNotRunning() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of());
        defaultMessageListenerContainerCoordinator.setApplicationContext(applicationContext);
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();
        defaultMessageListenerContainerCoordinator.start();

        // act
        defaultMessageListenerContainerCoordinator.stop();

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.isRunning()).isFalse();
    }

    @Test
    public void beanShouldBeStartedLast() {
        // arrange
        final DefaultMessageListenerContainerCoordinator defaultMessageListenerContainerCoordinator = new DefaultMessageListenerContainerCoordinator(ImmutableList.of());

        // assert
        assertThat(defaultMessageListenerContainerCoordinator.getPhase()).isEqualTo(Integer.MAX_VALUE);
    }

    @SuppressWarnings("WeakerAccess")
    public static class Bean {
        public void method() {

        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class BeanWithTwoMethods {
        public void methodOne() {

        }

        public void methodTwo() {

        }
    }
}
