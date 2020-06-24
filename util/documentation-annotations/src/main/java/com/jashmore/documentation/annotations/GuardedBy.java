package com.jashmore.documentation.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that the given field is protected from concurrency problems by the given object.
 */
@Documented
@Target({FIELD})
@Retention(SOURCE)
public @interface GuardedBy {
    String value();
}
