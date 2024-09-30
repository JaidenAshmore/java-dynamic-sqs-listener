package com.jashmore.sqs.micronaut.container;

import com.jashmore.sqs.container.MessageListenerContainer;

public interface DefaultMessageListenerContainerCoordinatorProperties {
    /**
     * Determine if the {@link MessageListenerContainer}s should be started up by default when the Micronaut application starts up.
     *
     * @return whether the containers will be started on startup
     */
    boolean isAutoStartContainersEnabled();
}
