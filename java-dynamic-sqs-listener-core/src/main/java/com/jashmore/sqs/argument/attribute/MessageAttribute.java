package com.jashmore.sqs.argument.attribute;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Populate on of the arguments in a message processing method by taking a value from the message attributes.
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface MessageAttribute {
    /**
     * The name of the attribute to use.
     *
     * @return the attribute name
     */
    String value();

    /**
     * Fail the processing of the message if the message attribute with the provided name does not exist.
     *
     * @return whether the message should fail to be processed if the message attribute is missing
     */
    boolean required() default false;
}
