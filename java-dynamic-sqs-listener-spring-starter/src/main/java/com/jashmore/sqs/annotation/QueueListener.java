package com.jashmore.sqs.annotation;


import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Mark a method as being the message consumer for a queue.
 */
@Retention(value = RUNTIME)
@Target(ElementType.METHOD)
public @interface QueueListener {
    /**
     * The queue name or url for the queue to listen to messages on.
     *
     * @return the queue name or URL of the queue
     */
    String value();
}
