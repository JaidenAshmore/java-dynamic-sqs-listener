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
 * consumer can consume and serialize the message payload between these versions.
 *
 * @param <T> the spring cloud registry schema type used to resolve this argument
 */
public class SpringCloudSchemaArgumentResolver<T> implements ArgumentResolver<Object> {
    private final SchemaReferenceExtractor schemaReferenceExtractor;
    private final ConsumerSchemaRetriever<T> consumerSchemaRetriever;
    private final ProducerSchemaRetriever<T> producerSchemaRetriever;
    private final MessagePayloadDeserializer<T> messagePayloadDeserializer;
    private final Class<? extends Annotation> annotationClass;

    /**
     * Constructor.
     *
     * @param schemaReferenceExtractor   used to obtain the {@link SchemaReference} of this message payload
     * @param consumerSchemaRetriever    used to obtain the schema that the consumer knows for this type
     * @param producerSchemaRetriever    used to obtain the schema that the producer used to send the message
     * @param messagePayloadDeserializer used to deserialize the message payload using the schemas for the message
     * @param annotationClass            the annotation that should be used to trigger this argument resolver
     */
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
        try {
            final Class<?> clazz = methodParameter.getParameter().getType();
            final SchemaReference schemaReference = schemaReferenceExtractor.extract(message);
            final T producerSchema = producerSchemaRetriever.getSchema(schemaReference);
            final T consumerSchema = consumerSchemaRetriever.getSchema(clazz);
            return messagePayloadDeserializer.deserialize(message, producerSchema, consumerSchema, clazz);
        } catch (RuntimeException runtimeException) {
            throw new ArgumentResolutionException(runtimeException);
        }
    }
}
