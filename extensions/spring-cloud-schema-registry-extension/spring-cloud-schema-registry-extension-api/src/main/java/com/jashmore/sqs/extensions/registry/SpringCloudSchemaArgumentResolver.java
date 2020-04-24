package com.jashmore.sqs.extensions.registry;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.util.annotation.AnnotationUtils;
import org.springframework.cloud.schema.registry.SchemaReference;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.annotation.Annotation;

/**
 * Argument resolver for taking messages that were serialized using a schema versioning tool like Apache Avro.
 *
 * <p>This will obtain the schema of the object that the producer used to serialize the object and the schema that the
 * consumer can consume and transform the message payload between these versions.
 */
public class SpringCloudSchemaArgumentResolver<T> implements ArgumentResolver<Object> {
    private final SchemaReferenceExtractor schemaReferenceExtractor;
    private final ConsumerSchemaRetriever<T> consumerSchemaRetriever;
    private final ProducerSchemaRetriever<T> producerSchemaRetriever;
    private final MessagePayloadDeserializer<T> messagePayloadDeserializer;
    private final Class<? extends Annotation> annotationClass;

    public SpringCloudSchemaArgumentResolver(final SchemaReferenceExtractor schemaReferenceExtractor,
                                             final ConsumerSchemaRetriever<T> consumerSchemaRetriever,
                                             final ProducerSchemaRetriever<T> producerSchemaRetriever,
                                             final MessagePayloadDeserializer<T> messagePayloadDeserializer,
                                             final Class<? extends Annotation> annotationClass) {
        this.schemaReferenceExtractor = schemaReferenceExtractor;
        this.consumerSchemaRetriever = consumerSchemaRetriever;
        this.producerSchemaRetriever = producerSchemaRetriever;
        this.messagePayloadDeserializer = messagePayloadDeserializer;
        this.annotationClass = annotationClass;
    }

    @Override
    public boolean canResolveParameter(final MethodParameter methodParameter) {
        return AnnotationUtils.findParameterAnnotation(methodParameter, annotationClass).isPresent();
    }

    @Override
    public Object resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final MethodParameter methodParameter,
                                              final Message message) throws ArgumentResolutionException {
        final Class<?> clazz = methodParameter.getParameter().getType();
        final SchemaReference schemaReference = schemaReferenceExtractor.extract(message);
        final T producerSchema = producerSchemaRetriever.getSchema(schemaReference);
        final T consumerSchema = consumerSchemaRetriever.getSchema(clazz);
        return messagePayloadDeserializer.deserialize(message, producerSchema, consumerSchema, clazz);
    }
}
