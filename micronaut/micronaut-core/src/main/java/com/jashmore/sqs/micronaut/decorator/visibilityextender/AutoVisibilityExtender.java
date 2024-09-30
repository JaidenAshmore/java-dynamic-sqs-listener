package com.jashmore.sqs.micronaut.decorator.visibilityextender;

import com.jashmore.sqs.decorator.AutoVisibilityExtenderMessageProcessingDecorator;
import com.jashmore.sqs.decorator.AutoVisibilityExtenderMessageProcessingDecoratorProperties;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation that can be attached to a message listener that will wrap the processing with logic that will automatically extend messages that take a
 * while to process.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface AutoVisibilityExtender {
    /**
     * The amount of time in seconds that the visibility of the message should be.
     *
     * @return the visibility timeout for any message in seconds
     * @see AutoVisibilityExtenderMessageProcessingDecoratorProperties#visibilityTimeout() for more details
     */
    int visibilityTimeoutInSeconds() default -1;

    /**
     * The amount of time in seconds that the visibility of the message should be.
     *
     * <p>This can be used when you need to load the value from Spring properties for example <pre>concurrencyLevelString = "${my.profile.property}"</pre>
     * instead of having it hardcoded in {@link #visibilityTimeoutInSeconds()}.
     *
     * <p>If this value is not empty, the value set by {@link #visibilityTimeoutInSeconds()} will be ignored.
     *
     * @return the visibility timeout for any message in seconds
     * @see AutoVisibilityExtenderMessageProcessingDecoratorProperties#visibilityTimeout() for more details
     */
    String visibilityTimeoutInSecondsString() default "";

    /**
     * The maximum amount of time in seconds a message should be processed before it should be stopped.
     *
     * @return the maximum processing time for a message
     * @see AutoVisibilityExtenderMessageProcessingDecoratorProperties#maxDuration() for more details
     */
    int maximumDurationInSeconds() default -1;

    /**
     * The maximum amount of time in seconds a message should be processed before it should be stopped.
     *
     * <p>This can be used when you need to load the value from Spring properties for example <pre>maximumDurationInSecondsString = "${my.profile.property}"</pre>
     * instead of having it hardcoded in {@link #maximumDurationInSeconds()}.
     *
     * <p>If this value is not empty, the value set by {@link #maximumDurationInSeconds()} will be ignored.
     *
     * @return the maximum processing time for a message
     * @see AutoVisibilityExtenderMessageProcessingDecoratorProperties#maxDuration() for more details
     */
    String maximumDurationInSecondsString() default "";

    /**
     * The length of time before the visibility timeout expires to actually call to extend it.
     *
     * <p>As there can be delays in the visibility extender from starting or makings calls out to AWS, this should be set to a value to give you ample room
     * to extend the visibility before it expires. This value must be less the provided visibility timeout of the message.
     *
     * <p>It can be also be used to support a higher guarantee of successfully extending the message if there is a blip in the success rate of extending the
     * messages. As the {@link AutoVisibilityExtenderMessageProcessingDecorator} does not attempt to fix failing extensions you can use this value
     * to make sure multiple attempts are made to extend the message before it will actually have the visibility expire. or example, you could have
     * the {@link #visibilityTimeoutInSeconds()} to be 30 seconds but this value to be 20 seconds and therefore you will have 3 attempts to successfully extend
     * the message before it expires.
     *
     * @return the buffer time before the visibility timeout expires to try and extend the visibility
     * @see AutoVisibilityExtenderMessageProcessingDecoratorProperties#bufferDuration() for more details
     */
    int bufferTimeInSeconds() default -1;

    /**
     * The length of time before the visibility timeout expires to actually call to extend it.
     *
     * <p>This can be used when you need to load the value from Spring properties for example <pre>bufferTimeInSecondsString = "${my.profile.property}"</pre>
     * instead of having it hardcoded in {@link #bufferTimeInSeconds()}.
     *
     * <p>If this value is not empty, the value set by {@link #bufferTimeInSeconds()} will be ignored.
     *
     * @return the buffer time before the visibility timeout expires to try and extend the visibility
     * @see #bufferTimeInSeconds() for more information about this field
     * @see AutoVisibilityExtenderMessageProcessingDecoratorProperties#bufferDuration() for more details
     */
    String bufferTimeInSecondsString() default "";
}
