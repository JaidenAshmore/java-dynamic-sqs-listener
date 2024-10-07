package com.jashmore.sqs.micronaut.container;

import com.jashmore.sqs.annotations.container.AnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.container.MessageListenerContainer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.AnyQualifier;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class MicronautMessageListenerContainerCoordinatorTest {

    @Mock
    private MicronautMessageListenerContainerCoordinatorProperties properties;

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void whenNoMessageListenerContainerFactoriesPresentBeansAreNotProcessed() {
        // arrange
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
            new MicronautMessageListenerContainerCoordinator(properties, emptyList(), applicationContext);

        // assert
        assertThat(micronautMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, never()).getAllBeanDefinitions();
    }

    @Test
    void settingApplicationContextTwiceDoesNothing() {
        // arrange
        final AnnotationMessageListenerContainerFactory<?> messageListenerContainerFactory = mock(AnnotationMessageListenerContainerFactory.class);
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
            new MicronautMessageListenerContainerCoordinator(
                    properties,
                    Collections.singletonList(messageListenerContainerFactory),
                    applicationContext);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(emptyList());

        // assert
        assertThat(micronautMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(applicationContext, times(1)).getAllBeanDefinitions();
    }

    @Test
    void buildsMessageListenContainersForEachEligibleBeanMethod() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final AnnotationMessageListenerContainerFactory messageListenerContainerFactory = mock(AnnotationMessageListenerContainerFactory.class);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(Optional.of(container));
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(beanDefinition.getDeclaredQualifier()).thenReturn(AnyQualifier.INSTANCE);
        when(applicationContext.containsBean(eq(Bean.class), eq(AnyQualifier.INSTANCE)))
                .thenReturn(true);
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
                new MicronautMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // assert
        assertThat(micronautMessageListenerContainerCoordinator.getContainers()).containsOnly(container);
    }

    @Test
    void doesNotBuildMessageListenContainerIfContextDoesNotContainBean() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final AnnotationMessageListenerContainerFactory messageListenerContainerFactory = mock(AnnotationMessageListenerContainerFactory.class);
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(beanDefinition.getDeclaredQualifier()).thenReturn(AnyQualifier.INSTANCE);
        when(applicationContext.containsBean(eq(Bean.class), eq(AnyQualifier.INSTANCE)))
                .thenReturn(false);
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
                new MicronautMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // assert
        assertThat(micronautMessageListenerContainerCoordinator.getContainers()).isEmpty();
        verify(messageListenerContainerFactory, never()).buildContainer(bean, method);
        // also important to not call getBean, which may inadvertently create a bean
        verify(applicationContext, never()).getBean(beanDefinition);
    }

    @Test
    void duplicateMessageListenerContainsThrowsExceptionWhenStarting() throws NoSuchMethodException {
        // arrange
        final BeanWithTwoMethods bean = new BeanWithTwoMethods();
        final Method methodOne = bean.getClass().getMethod("methodOne");
        final AnnotationMessageListenerContainerFactory messageListenerContainerFactory = mock(AnnotationMessageListenerContainerFactory.class);

        final MessageListenerContainer containerOne = mock(MessageListenerContainer.class);
        when(containerOne.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, methodOne)).thenReturn(Optional.of(containerOne));

        final Method methodTwo = bean.getClass().getMethod("methodTwo");
        final MessageListenerContainer containerTwo = mock(MessageListenerContainer.class);
        when(containerTwo.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, methodTwo)).thenReturn(Optional.of(containerTwo));

        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(BeanWithTwoMethods.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(beanDefinition.getDeclaredQualifier()).thenReturn(AnyQualifier.INSTANCE);
        when(applicationContext.containsBean(eq(BeanWithTwoMethods.class), eq(AnyQualifier.INSTANCE)))
                .thenReturn(true);
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
                new MicronautMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // act
        assertThrows(IllegalStateException.class, micronautMessageListenerContainerCoordinator::startAllContainers);
    }

    @Test
    void startingContainersWillStartAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final AnnotationMessageListenerContainerFactory messageListenerContainerFactory = mock(AnnotationMessageListenerContainerFactory.class);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(Optional.of(container));
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(beanDefinition.getDeclaredQualifier()).thenReturn(AnyQualifier.INSTANCE);
        when(applicationContext.containsBean(eq(Bean.class), eq(AnyQualifier.INSTANCE)))
                .thenReturn(true);
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
                new MicronautMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        assertThat(micronautMessageListenerContainerCoordinator.getContainers()).hasSize(1);

        // act
        micronautMessageListenerContainerCoordinator.startAllContainers();

        // assert
        verify(container).start();
    }

    @Test
    void stoppingAllContainersWillStopAllMessageListenerContainersBuilt() throws NoSuchMethodException {
        // arrange
        log.debug("Starting stoppingAllContainersWillStopAllMessageListenerContainersBuilt");
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final AnnotationMessageListenerContainerFactory messageListenerContainerFactory = mock(AnnotationMessageListenerContainerFactory.class);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(Optional.of(container));
        doAnswer(invocationOnMock -> {
            log.info("Stopping container");
            return null;
        })
            .when(container)
            .stop();
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(beanDefinition.getDeclaredQualifier()).thenReturn(AnyQualifier.INSTANCE);
        when(applicationContext.containsBean(eq(Bean.class), eq(AnyQualifier.INSTANCE)))
                .thenReturn(true);
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
                new MicronautMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);
        assertThat(micronautMessageListenerContainerCoordinator.getContainers()).hasSize(1);

        // act
        micronautMessageListenerContainerCoordinator.stopAllContainers();
        log.info("Should have stopped all containers");

        // assert
        verify(container).stop();
    }

    @Test
    void stoppingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
            new MicronautMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);

        // act
        assertThrows(IllegalArgumentException.class, () -> micronautMessageListenerContainerCoordinator.stopContainer("unknown"));
    }

    @Test
    void stoppingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final AnnotationMessageListenerContainerFactory messageListenerContainerFactory = mock(AnnotationMessageListenerContainerFactory.class);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(Optional.of(container));
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(beanDefinition.getDeclaredQualifier()).thenReturn(AnyQualifier.INSTANCE);
        when(applicationContext.containsBean(eq(Bean.class), eq(AnyQualifier.INSTANCE)))
                .thenReturn(true);
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
                new MicronautMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // act
        micronautMessageListenerContainerCoordinator.stopContainer("identifier");

        // assert
        verify(container).stop();
    }

    @Test
    void startingContainerThatDoesNotExistThrowsIllegalArgumentException() {
        // arrange
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
            new MicronautMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);

        // act
        assertThrows(IllegalArgumentException.class, () -> micronautMessageListenerContainerCoordinator.startContainer("unknown"));
    }

    @Test
    void startingIndividualContainerWithIdentifierCallsStopOnContainer() throws NoSuchMethodException {
        // arrange
        final Bean bean = new Bean();
        final Method method = bean.getClass().getMethod("method");
        final AnnotationMessageListenerContainerFactory messageListenerContainerFactory = mock(AnnotationMessageListenerContainerFactory.class);
        final MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.getIdentifier()).thenReturn("identifier");
        when(messageListenerContainerFactory.buildContainer(bean, method)).thenReturn(Optional.of(container));
        BeanDefinition beanDefinition = mock(BeanDefinition.class);
        when(beanDefinition.getBeanType()).thenReturn(Bean.class);
        when(applicationContext.getAllBeanDefinitions()).thenReturn(List.of(beanDefinition));
        when(beanDefinition.getDeclaredQualifier()).thenReturn(AnyQualifier.INSTANCE);
        when(applicationContext.containsBean(eq(Bean.class), eq(AnyQualifier.INSTANCE)))
                .thenReturn(true);
        when(applicationContext.getBean(beanDefinition)).thenReturn(bean);
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
                new MicronautMessageListenerContainerCoordinator(
                        properties,
                        Collections.singletonList(messageListenerContainerFactory),
                        applicationContext);

        // act
        micronautMessageListenerContainerCoordinator.startContainer("identifier");

        // assert
        verify(container).start();
    }

    @Test
    void startLifeCycleStartsAllContainers() {
        // arrange
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator = spy(
            new MicronautMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext)
        );

        // act
        micronautMessageListenerContainerCoordinator.start();

        // assert
        verify(micronautMessageListenerContainerCoordinator).startAllContainers();
    }

    @Test
    void stopLifeCycleStopsAllContainers() {
        // arrange
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator = spy(
            new MicronautMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext)
        );

        // act
        micronautMessageListenerContainerCoordinator.stop();

        // assert
        verify(micronautMessageListenerContainerCoordinator).stopAllContainers();
    }

    @Test
    void beanIsNotRunningWhenStartIsNotCalled() {
        // arrange
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
            new MicronautMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);

        // assert
        assertThat(micronautMessageListenerContainerCoordinator.isRunning()).isFalse();
    }

    @Test
    void startLifeCycleSetsBeanAsRunning() {
        // arrange
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
            new MicronautMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);
        assertThat(micronautMessageListenerContainerCoordinator.isRunning()).isFalse();

        // act
        micronautMessageListenerContainerCoordinator.start();

        // assert
        assertThat(micronautMessageListenerContainerCoordinator.isRunning()).isTrue();
    }

    @Test
    void stopLifeCycleSetsBeanAsNotRunning() {
        // arrange
        final MicronautMessageListenerContainerCoordinator micronautMessageListenerContainerCoordinator =
            new MicronautMessageListenerContainerCoordinator(
                    properties,
                    emptyList(),
                    applicationContext);
        assertThat(micronautMessageListenerContainerCoordinator.isRunning()).isFalse();
        micronautMessageListenerContainerCoordinator.start();

        // act
        micronautMessageListenerContainerCoordinator.stop();

        // assert
        assertThat(micronautMessageListenerContainerCoordinator.isRunning()).isFalse();
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
