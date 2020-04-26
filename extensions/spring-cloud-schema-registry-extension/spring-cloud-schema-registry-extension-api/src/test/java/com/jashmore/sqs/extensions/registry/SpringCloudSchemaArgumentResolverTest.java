package com.jashmore.sqs.extensions.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.schema.registry.SchemaReference;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;

class SpringCloudSchemaArgumentResolverTest {
    @Test
    void willAllowMethodParametersToBeProcessedWithCertainAnnotation() throws NoSuchMethodException {
        // arrange
        final SpringCloudSchemaArgumentResolver<Integer> resolver = new SpringCloudSchemaArgumentResolver<>(
                message -> new SchemaReference("name", 1, "numbered"),
                (clazz) -> 2,
                (reference) -> 1,
                (message, producerSchema, consumerSchema, clazz) -> "value",
                SpringCloudSchemaRegistryPayload.class
        );
        final Method method = SpringCloudSchemaArgumentResolverTest.class.getMethod("myMethod", String.class, String.class);
        final MethodParameter methodParameter = new DefaultMethodParameter(
                method,
                method.getParameters()[0],
                0
        );
        final MethodParameter secondMethodParameter = new DefaultMethodParameter(
                method,
                method.getParameters()[1],
                1
        );

        // act
        assertThat(resolver.canResolveParameter(methodParameter)).isTrue();
        assertThat(resolver.canResolveParameter(secondMethodParameter)).isFalse();
    }

    @Test
    void willObtainSchemasAndDeserializeWithTheProvidedValues() throws NoSuchMethodException {
        // arrange
        final SpringCloudSchemaArgumentResolver<Integer> resolver = new SpringCloudSchemaArgumentResolver<>(
                message -> new SchemaReference("name", 1, "numbered"),
                (clazz) -> 2,
                (reference) -> 1,
                (message, producerSchema, consumerSchema, clazz)
                        -> "producer: " + producerSchema + " consumer: " + consumerSchema + " class: " + clazz.getSimpleName(),
                SpringCloudSchemaRegistryPayload.class
        );
        final Method method = SpringCloudSchemaArgumentResolverTest.class.getMethod("myMethod", String.class, String.class);
        final MethodParameter methodParameter = new DefaultMethodParameter(
                method,
                method.getParameters()[0],
                0
        );

        // act
        final Object value = resolver.resolveArgumentForParameter(QueueProperties.builder().build(), methodParameter, Message.builder().build());

        // assert
        assertThat(value).isEqualTo("producer: 1 consumer: 2 class: String");
    }

    @Test
    void anyExceptionDuringResolutionThrowsArgumentResolutionException() throws NoSuchMethodException {
        // arrange
        final ProducerSchemaRetrieverException producerSchemaRetrieverException
                = new ProducerSchemaRetrieverException(new RuntimeException("Expected Test Exception"));
        final SpringCloudSchemaArgumentResolver<Integer> resolver = new SpringCloudSchemaArgumentResolver<>(
                message -> new SchemaReference("name", 1, "numbered"),
                (clazz) -> {
                    throw producerSchemaRetrieverException;
                },
                (reference) -> 1,
                (message, producerSchema, consumerSchema, clazz)
                        -> "producer: " + producerSchema + " consumer: " + consumerSchema + " class: " + clazz.getSimpleName(),
                SpringCloudSchemaRegistryPayload.class
        );
        final Method method = SpringCloudSchemaArgumentResolverTest.class.getMethod("myMethod", String.class, String.class);
        final MethodParameter methodParameter = new DefaultMethodParameter(
                method,
                method.getParameters()[0],
                0
        );

        // act
        final ArgumentResolutionException exception = assertThrows(ArgumentResolutionException.class,
                () -> resolver.resolveArgumentForParameter(QueueProperties.builder().build(), methodParameter, Message.builder().build()));

        // assert
        assertThat(exception).hasCause(producerSchemaRetrieverException);
    }


    public void myMethod(@SpringCloudSchemaRegistryPayload String exampleParameter, String anotherPayload) {

    }
}