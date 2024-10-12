package com.jashmore.sqs.micronaut.container;

import com.jashmore.sqs.container.MessageListenerContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicronautMessageListenerContainerRegistryTest {

    @Mock
    private MessageListenerContainer container;

    private MicronautMessageListenerContainerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MicronautMessageListenerContainerRegistry();
    }

    @Test
    void putContainer() {
        when(container.getIdentifier()).thenReturn("id");
        registry.put(container);
        assertEquals(1, registry.getContainerMap().size());
        assertEquals(container, registry.getContainerMap().get("id"));
    }

    @Test
    void putContainerWithSameIdThrows() {
        when(container.getIdentifier()).thenReturn("id");
        registry.put(container);
        MessageListenerContainer anotherContainer = mock(MessageListenerContainer.class);
        when(anotherContainer.getIdentifier()).thenReturn("id");
        assertThrows(IllegalStateException.class, () -> registry.put(anotherContainer));
    }
}