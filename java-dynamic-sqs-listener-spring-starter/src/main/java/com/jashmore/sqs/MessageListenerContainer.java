package com.jashmore.sqs;

import java.util.concurrent.Future;

/**
 * Container that is used for the spring starter to wrap all of the components
 */
public interface MessageListenerContainer {
    void start();

    Future<?> stop();
}
