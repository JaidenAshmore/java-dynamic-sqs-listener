package com.jashmore.sqs.spring.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.spring.QueueWrapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

public class DefaultQueueContainerServiceTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ApplicationContext applicationContext;

    @Test
    public void whenNoQueueWrappersPresentBeansAreNotProcessed() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of());

        // act
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // assert
        assertThat(defaultQueueContainerService.getContainers()).isEmpty();
        verify(applicationContext, never()).getBeanDefinitionNames();
    }

    @Test
    public void settingApplicationContextTwiceDoesNothing() {
        // arrange
        final QueueWrapper queueWrapper = mock(QueueWrapper.class);
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of(queueWrapper));
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { });

        // act
        defaultQueueContainerService.setApplicationContext(applicationContext);
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // assert
        assertThat(defaultQueueContainerService.getContainers()).isEmpty();
        verify(applicationContext, times(1)).getBeanDefinitionNames();
    }

    @Test
    public void buildsMessageListenContainersForEachEligibleBeanMethod() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final QueueWrapper queueWrapper = mock(QueueWrapper.class);
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of(queueWrapper));
        when(queueWrapper.canWrapMethod(any(Method.class))).thenReturn(false);
        when(queueWrapper.canWrapMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(queueWrapper.wrapMethod(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);

        // act
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // assert
        assertThat(defaultQueueContainerService.getContainers()).containsOnly(container);
    }

    @Test
    public void methodsThatAreNotEligibleForWrappingWillNotCreateMessageListeners() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final QueueWrapper queueWrapper = mock(QueueWrapper.class);
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of(queueWrapper));
        when(queueWrapper.canWrapMethod(any(Method.class))).thenReturn(false);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(queueWrapper.wrapMethod(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);

        // act
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // assert
        verify(queueWrapper, never()).wrapMethod(bean, method);
        assertThat(defaultQueueContainerService.getContainers()).isEmpty();
    }

    @Test
    public void duplicateMessageListenerContainsThrowsExceptionOnInitialisation() throws NoSuchMethodException {
        // arrange
        final BeanWithTwoMethods bean = new BeanWithTwoMethods();
        final Method methodOne = bean.getClass().getMethod("methodOne");
        final Method methodTwo = bean.getClass().getMethod("methodTwo");
        final QueueWrapper queueWrapper = mock(QueueWrapper.class);
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of(queueWrapper));
        when(queueWrapper.canWrapMethod(any(Method.class))).thenReturn(false);
        when(queueWrapper.canWrapMethod(methodOne)).thenReturn(true);
        when(queueWrapper.canWrapMethod(methodTwo)).thenReturn(true);
        final MessageListenerContainer containerOne = mock(MessageListenerContainer.class);
        when(containerOne.getIdentifier()).thenReturn("identifier");
        when(queueWrapper.wrapMethod(bean, methodOne)).thenReturn(containerOne);
        final MessageListenerContainer containerTwo = mock(MessageListenerContainer.class);
        when(queueWrapper.wrapMethod(bean, methodTwo)).thenReturn(containerTwo);
        when(containerTwo.getIdentifier()).thenReturn("identifier");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        expectedException.expect(IllegalStateException.class);

        // act
        defaultQueueContainerService.setApplicationContext(applicationContext);
    }

    @Test
    public void startingContainersWillStartAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final QueueWrapper queueWrapper = mock(QueueWrapper.class);
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of(queueWrapper));
        when(queueWrapper.canWrapMethod(any(Method.class))).thenReturn(false);
        when(queueWrapper.canWrapMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(queueWrapper.wrapMethod(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // act
        defaultQueueContainerService.startAllContainers();

        // assert
        verify(container).start();
    }

    @Test
    public void stoppingAllContainersWillStopAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final QueueWrapper queueWrapper = mock(QueueWrapper.class);
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of(queueWrapper));
        when(queueWrapper.canWrapMethod(any(Method.class))).thenReturn(false);
        when(queueWrapper.canWrapMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(queueWrapper.wrapMethod(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // act
        defaultQueueContainerService.stopAllContainers();

        // assert
        verify(container).stop();
    }

    @Test
    public void stoppingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of());
        defaultQueueContainerService.setApplicationContext(applicationContext);
        expectedException.expect(IllegalArgumentException.class);

        // act
        defaultQueueContainerService.stopContainer("unknown");
    }

    @Test
    public void stoppingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final QueueWrapper queueWrapper = mock(QueueWrapper.class);
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of(queueWrapper));
        when(queueWrapper.canWrapMethod(any(Method.class))).thenReturn(false);
        when(queueWrapper.canWrapMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(queueWrapper.wrapMethod(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // act
        defaultQueueContainerService.stopContainer("identifier");

        // assert
        verify(container).stop();
    }

    @Test
    public void startingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of());
        defaultQueueContainerService.setApplicationContext(applicationContext);
        expectedException.expect(IllegalArgumentException.class);

        // act
        defaultQueueContainerService.startContainer("unknown");
    }

    @Test
    public void startingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final QueueWrapper queueWrapper = mock(QueueWrapper.class);
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of(queueWrapper));
        when(queueWrapper.canWrapMethod(any(Method.class))).thenReturn(false);
        when(queueWrapper.canWrapMethod(method)).thenReturn(true);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(queueWrapper.wrapMethod(bean, method)).thenReturn(container);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] { "bean" });
        when(applicationContext.getBean("bean")).thenReturn(bean);
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // act
        defaultQueueContainerService.startContainer("identifier");

        // assert
        verify(container).start();
    }

    @Test
    public void autoStartsService() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of());

        // assert
        assertThat(defaultQueueContainerService.isAutoStartup()).isTrue();
    }

    @Test
    public void startLifeCycleStartsAllContainers() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = spy(new DefaultQueueContainerService(ImmutableList.of()));
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // act
        defaultQueueContainerService.start();

        // assert
        verify(defaultQueueContainerService).startAllContainers();
    }

    @Test
    public void stopLifeCycleStopsAllContainers() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = spy(new DefaultQueueContainerService(ImmutableList.of()));
        defaultQueueContainerService.setApplicationContext(applicationContext);

        // act
        defaultQueueContainerService.stop();

        // assert
        verify(defaultQueueContainerService).stopAllContainers();
    }

    @Test
    public void stopLifeCycleWithCallbackStartsAllContainersAndRunsCallback() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = spy(new DefaultQueueContainerService(ImmutableList.of()));
        defaultQueueContainerService.setApplicationContext(applicationContext);
        final Runnable callback = mock(Runnable.class);

        // act
        defaultQueueContainerService.stop(callback);

        // assert
        verify(defaultQueueContainerService).stop();
        verify(callback).run();
    }

    @Test
    public void beanIsNotRunningWhenStartIsNotCalled() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of());

        // assert
        assertThat(defaultQueueContainerService.isRunning()).isFalse();
    }

    @Test
    public void startLifeCycleSetsBeanAsRunning() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of());
        defaultQueueContainerService.setApplicationContext(applicationContext);
        assertThat(defaultQueueContainerService.isRunning()).isFalse();

        // act
        defaultQueueContainerService.start();

        // assert
        assertThat(defaultQueueContainerService.isRunning()).isTrue();
    }

    @Test
    public void stopLifeCycleSetsBeanAsNotRunning() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of());
        defaultQueueContainerService.setApplicationContext(applicationContext);
        assertThat(defaultQueueContainerService.isRunning()).isFalse();
        defaultQueueContainerService.start();

        // act
        defaultQueueContainerService.stop();

        // assert
        assertThat(defaultQueueContainerService.isRunning()).isFalse();
    }

    @Test
    public void beanShouldBeStartedLast() {
        // arrange
        final DefaultQueueContainerService defaultQueueContainerService = new DefaultQueueContainerService(ImmutableList.of());

        // assert
        assertThat(defaultQueueContainerService.getPhase()).isEqualTo(Integer.MAX_VALUE);
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
