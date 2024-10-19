package com.jashmore.sqs.micronaut.container;

import com.jashmore.sqs.container.MessageListenerContainer;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties(MicronautMessageListenerContainerCoordinatorProperties.PREFIX)
public interface MicronautMessageListenerContainerCoordinatorProperties {
    String PREFIX = "java-dynamic-sqs-listener-micronaut";

    /**
     * Determine if the {@link MessageListenerContainer}s should be started up by default
     * when the Micronaut application starts up.
     *
     * @return whether the containers will be started on startup
     */
    @Bindable(defaultValue = "true")
    boolean isAutoStartContainersEnabled();
}
