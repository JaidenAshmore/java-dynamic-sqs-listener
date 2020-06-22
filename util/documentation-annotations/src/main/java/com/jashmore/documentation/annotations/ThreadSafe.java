package com.jashmore.documentation.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that the given class or method must be Thread Safe as it will be used concurrently.
 */
@Documented
@Target({TYPE, METHOD})
@Retention(SOURCE)
public @interface ThreadSafe {
}
