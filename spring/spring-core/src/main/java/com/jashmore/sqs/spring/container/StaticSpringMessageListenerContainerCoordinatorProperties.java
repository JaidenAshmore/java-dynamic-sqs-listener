package com.jashmore.sqs.spring.container;

import lombok.Builder;
import lombok.Value;

/**
 * Provides the properties as static values that never change.
 */
@Value
@Builder(toBuilder = true)
public class StaticSpringMessageListenerContainerCoordinatorProperties implements SpringMessageListenerContainerCoordinatorProperties {

    boolean isAutoStartContainersEnabled;

    @Override
    public boolean isAutoStartContainersEnabled() {
        return isAutoStartContainersEnabled;
    }
}
