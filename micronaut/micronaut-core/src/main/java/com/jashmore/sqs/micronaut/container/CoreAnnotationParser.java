package com.jashmore.sqs.micronaut.container;

import java.lang.annotation.Annotation;

/**
 * Parser used for converting an annotation into the properties needed for one of the core {@link com.jashmore.sqs.container.MessageListenerContainer}s.
 */
public interface CoreAnnotationParser<A extends Annotation, P> {
    /**
     * Parse the supplied annotation into the properties to build a {@link com.jashmore.sqs.container.MessageListenerContainer}.
     *
     * @param annotation the annotation to parse
     * @return the container properties
     */
    P parse(A annotation);
}
