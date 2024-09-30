package com.jashmore.sqs.micronaut.container;

import lombok.Builder;
import lombok.Value;

/**
 * Provides the properties as static values that never change.
 */
@Value
@Builder(toBuilder = true)
public class StaticDefaultMessageListenerContainerCoordinatorProperties implements DefaultMessageListenerContainerCoordinatorProperties {

    boolean isAutoStartContainersEnabled;

    @Override
    public boolean isAutoStartContainersEnabled() {
        return isAutoStartContainersEnabled;
    }
}
