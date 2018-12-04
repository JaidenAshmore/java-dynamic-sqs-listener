package com.jashmore.sqs.argument.messageid;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.jashmore.sqs.argument.ArgumentResolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation for a message consumer's parameter that indicate that it should be resolved to contain the message ID of the message.
 *
 * <p>Parameters marked with this annotation must be of type {@link String} and should not be annotated with any other annotations or types
 * that would be resolved by an {@link ArgumentResolver}.
 */
@Retention(value = RUNTIME)
@Target(ElementType.PARAMETER)
public @interface MessageId {
}
