package com.jashmore.sqs.extensions.registry;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.extensions.registry.consumer.ConsumerSchemaProvider;
import com.jashmore.sqs.extensions.registry.deserializer.MessagePayloadDeserializer;
import com.jashmore.sqs.extensions.registry.producer.ProducerSchemaProvider;
import com.jashmore.sqs.extensions.registry.schemareference.SchemaReferenceExtractor;
import com.jashmore.sqs.util.annotation.AnnotationUtils;
import org.springframework.cloud.schema.registry.SchemaReference;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Argument resolver for taking messages that were serialized using a schema versioning tool like Apache Avro.
 *
 * <p>This will obtain the schema of the object that the producer used to serialize the object and the schema that the
 * consumer can consume and transform the message payload between these versions. Note that the consumer must be at a
 * later version of the schema otherwise there isn't a way to know how to serialize it to a state that the consumer
 * doesn't know about.
 */
public class SpringCloudSchemaArgumentResolver<T> implements ArgumentResolver<Object> {
    private final SchemaReferenceExtractor schemaReferenceExtractor;
    private final ConsumerSchemaProvider<T> consumerSchemaProvider;
    private final ProducerSchemaProvider<T> producerSchemaProvider;
    private final MessagePayloadDeserializer<T> messagePayloadDeserializer;

    public SpringCloudSchemaArgumentResolver(final SchemaReferenceExtractor schemaReferenceExtractor,
                                             final ConsumerSchemaProvider<T> consumerSchemaProvider,
                                             final ProducerSchemaProvider<T> producerSchemaProvider,
                                             final MessagePayloadDeserializer<T> messagePayloadDeserializer) {
        this.schemaReferenceExtractor = schemaReferenceExtractor;
        this.consumerSchemaProvider = consumerSchemaProvider;
        this.producerSchemaProvider = producerSchemaProvider;
        this.messagePayloadDeserializer = messagePayloadDeserializer;
    }

    @Override
    public boolean canResolveParameter(final MethodParameter methodParameter) {
        return AnnotationUtils.findParameterAnnotation(methodParameter, SpringRegistryPayload.class).isPresent();
    }

    @Override
    public Object resolveArgumentForParameter(final QueueProperties queueProperties,
                                              final MethodParameter methodParameter,
                                              final Message message) throws ArgumentResolutionException {
        final Class<?> clazz = methodParameter.getParameter().getType();
        final SchemaReference schemaReference = schemaReferenceExtractor.extract(message);
        final T producerSchema = producerSchemaProvider.getSchema(schemaReference);
        final T consumerSchema = consumerSchemaProvider.get(clazz);
        return messagePayloadDeserializer.deserialize(message, producerSchema, consumerSchema, clazz);
    }
}
