package com.jashmore.sqs.micronaut.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrefetchingQueueListenerAnnotationTransformerTest {

    @Mock
    private VisitorContext visitorContext;

    private PrefetchingQueueListenerAnnotationTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new PrefetchingQueueListenerAnnotationTransformer();
    }

    @Test
    void testGetName() {
        String expectedName = "com.jashmore.sqs.annotations.core.prefetch.PrefetchingQueueListener";
        String actualName = transformer.getName();
        assertEquals(expectedName, actualName);
    }

    @Test
    void testTransform() {
        AnnotationValue<Annotation> annotationValue = AnnotationValue
            .builder("PrefetchingQueueListener")
            .member("concurrencyLevel", 5)
            .build();
        List<AnnotationValue<?>> transformedAnnotations = transformer.transform(annotationValue, visitorContext);
        assertThat(transformedAnnotations).hasSize(1);
        AnnotationValue<?> transformedAnnotation = transformedAnnotations.get(0);
        assertThat(transformedAnnotation.get("concurrencyLevel", Integer.class)).isEqualTo(Optional.of(5));
        assertThat(transformedAnnotation.getStereotypes())
            .containsExactly(AnnotationValue.builder(Executable.class).member("processOnStartup", true).build());
    }

    @Test
    void transformWithNullAnnotationValue() {
        assertThrows(NullPointerException.class, () -> transformer.transform(null, visitorContext));
    }
}
