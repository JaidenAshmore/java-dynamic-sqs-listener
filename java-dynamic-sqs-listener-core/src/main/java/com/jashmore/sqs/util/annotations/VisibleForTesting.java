package com.jashmore.sqs.util.annotations;


import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;

/**
 * Annotation used to indicate that the visibility of a component has been increased for testing purposes.
 *
 * <p>This has been implemented again to reduce dependencies on other modules, more specifically Guava.
 */
@Retention(value = SOURCE)
public @interface VisibleForTesting {
}
