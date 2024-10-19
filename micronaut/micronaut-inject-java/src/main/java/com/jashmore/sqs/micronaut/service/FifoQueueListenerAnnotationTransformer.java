package com.jashmore.sqs.micronaut.service;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import java.lang.annotation.Annotation;
import java.util.List;

public class FifoQueueListenerAnnotationTransformer implements NamedAnnotationTransformer {

    @Override
    public @NonNull String getName() {
        return "com.jashmore.sqs.annotations.core.fifo.FifoQueueListener";
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return List.of(
            annotation.mutate().stereotype(AnnotationValue.builder(Executable.class).member("processOnStartup", true).build()).build()
        );
    }
}
