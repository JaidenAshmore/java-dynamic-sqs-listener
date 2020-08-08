package com.jashmore.sqs.spring.container;

import com.jashmore.sqs.container.MessageListenerContainer;

public interface DefaultMessageListenerContainerCoordinatorProperties {
    /**
     * Determine if the {@link MessageListenerContainer}s should be started up by default when the Spring Container starts up.
     *
     * @return whether the containers will be started on startup
     */
    boolean isAutoStartContainersEnabled();
}
