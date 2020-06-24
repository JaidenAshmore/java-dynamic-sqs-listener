package com.jashmore.sqs.argument.attribute;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Populate on of the arguments in a message processing method by taking a value from the message system attributes.
 *
 * <p>There are currently a subset of types that can be used for this parameter. These include:
 *
 * <ul>
 *     <li>{@link String}: all attributes can be cast to a String</li>
 *     <li>{@link Integer}, int, {@link Long}, long: any attributes that are in number format can be cast to these. These include:
 *          <ul>
 *              <li>{@link MessageSystemAttributeName#SENT_TIMESTAMP}</li>
 *              <li>{@link MessageSystemAttributeName#APPROXIMATE_RECEIVE_COUNT}</li>
 *              <li>{@link MessageSystemAttributeName#APPROXIMATE_FIRST_RECEIVE_TIMESTAMP}</li>
 *          </ul>
 *     </li>
 *     <li>{@link java.time.OffsetDateTime}, {@link java.time.Instant}: attributes that are for timestamps can be cast to this. These include:
 *         <ul>
 *             <li>{@link MessageSystemAttributeName#SENT_TIMESTAMP}</li>
 *             <li>{@link MessageSystemAttributeName#APPROXIMATE_FIRST_RECEIVE_TIMESTAMP}</li>
 *         </ul>
 *     </li>
 * </ul>
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface MessageSystemAttribute {
    /**
     * The system attribute that should be used to populate this parameter.
     *
     * @return the attribute to consume
     */
    MessageSystemAttributeName value();

    /**
     * Fail the processing of the message if the message attribute with the provided name does not exist.
     *
     * @return whether the message should fail to be processed if the message attribute is missing
     */
    boolean required() default false;
}
