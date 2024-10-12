package com.jashmore.sqs.micronaut.container;

import com.jashmore.sqs.container.MessageListenerContainer;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
@Slf4j
public class MicronautMessageListenerContainerRegistry {

    private final ConcurrentMap<String, MessageListenerContainer> messageListenerContainers = new ConcurrentHashMap<>();

    synchronized void put(MessageListenerContainer container) {
        if (messageListenerContainers.containsKey(container.getIdentifier())) {
            throw new IllegalStateException(
                    "Created two MessageListenerContainers with the same identifier: " +
                            container.getIdentifier()
            );
        }
        log.debug("Created MessageListenerContainer with id: {}", container.getIdentifier());
        messageListenerContainers.put(container.getIdentifier(), container);
    }

    Map<String, MessageListenerContainer> getContainerMap() {
        return Map.copyOf(messageListenerContainers);
    }
}
