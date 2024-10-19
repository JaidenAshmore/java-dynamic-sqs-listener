package com.jashmore.sqs.micronaut.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.jashmore.sqs.container.MessageListenerContainer;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Slf4j
@ExtendWith(MockitoExtension.class)
class MicronautMessageListenerContainerCoordinatorTest {

    @Mock
    private MicronautMessageListenerContainerCoordinatorProperties properties;

    @Mock
    private MicronautMessageListenerContainerRegistry containerRegistry;

    @Test
    void whenRegistryIsEmptyGetContainersIsEmpty() {
        // arrange
        final MicronautMessageListenerContainerCoordinator coordinator = new MicronautMessageListenerContainerCoordinator(
            properties,
            containerRegistry
        );
        when(containerRegistry.getContainerMap()).thenReturn(Collections.emptyMap());

        // assert
        assertThat(coordinator.getContainers()).isEmpty();
    }

    @Test
    void whenRegistryHasContainersTheyAreStarted() {
        final MicronautMessageListenerContainerCoordinator coordinator = new MicronautMessageListenerContainerCoordinator(
            properties,
            containerRegistry
        );
        Map<String, MessageListenerContainer> containerMap = Map.of(
            "a",
            mock(MessageListenerContainer.class),
            "b",
            mock(MessageListenerContainer.class),
            "c",
            mock(MessageListenerContainer.class)
        );
        when(containerRegistry.getContainerMap()).thenReturn(containerMap);

        coordinator.start();

        assertThat(coordinator.getContainers()).hasSize(3);
        verify(containerMap.get("a")).start();
        verify(containerMap.get("b")).start();
        verify(containerMap.get("c")).start();
    }

    @Test
    void isRunningIsCorrect() {
        final MicronautMessageListenerContainerCoordinator coordinator = new MicronautMessageListenerContainerCoordinator(
            properties,
            containerRegistry
        );
        Map<String, MessageListenerContainer> containerMap = Map.of("a", mock(MessageListenerContainer.class));
        when(containerRegistry.getContainerMap()).thenReturn(containerMap);

        assertThat(coordinator.isRunning()).isFalse();

        coordinator.start();
        assertThat(coordinator.isRunning()).isTrue();

        coordinator.stop();
        assertThat(coordinator.isRunning()).isFalse();
    }
}
