package com.jashmore.sqs.micronaut.service;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class FifoQueueListenerAnnotationTransformerTest {

    @Mock
    private VisitorContext visitorContext;

    private FifoQueueListenerAnnotationTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new FifoQueueListenerAnnotationTransformer();
    }

    @Test
    void testGetName() {
        String expectedName = "com.jashmore.sqs.annotations.core.fifo.FifoQueueListener";
        String actualName = transformer.getName();
        assertEquals(expectedName, actualName);
    }

    @Test
    void testTransform() {
        AnnotationValue<Annotation> annotationValue = AnnotationValue.builder("FifoQueueListener")
                .member("concurrencyLevel", 5)
                .build();
        List<AnnotationValue<?>> transformedAnnotations = transformer.transform(annotationValue, visitorContext);
        assertThat(transformedAnnotations).hasSize(1);
        AnnotationValue<?> transformedAnnotation = transformedAnnotations.get(0);
        assertThat(transformedAnnotation.get("concurrencyLevel", Integer.class))
                .isEqualTo(Optional.of(5));
        assertThat(transformedAnnotation.getStereotypes()).containsExactly(
                AnnotationValue.builder(Executable.class)
                        .member("processOnStartup", true)
                        .build());
    }

    @Test
    void transformWithNullAnnotationValue() {
        assertThrows(NullPointerException.class, () -> transformer.transform(null, visitorContext));
    }
}