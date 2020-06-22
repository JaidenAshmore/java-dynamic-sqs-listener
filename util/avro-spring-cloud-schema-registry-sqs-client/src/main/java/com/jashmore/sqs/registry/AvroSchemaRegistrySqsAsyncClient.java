package com.jashmore.sqs.registry;

import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toMap;
import static org.springframework.cloud.schema.registry.avro.AvroSchemaRegistryClientMessageConverter.AVRO_FORMAT;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.springframework.cloud.schema.registry.SchemaReference;
import org.springframework.cloud.schema.registry.SchemaRegistrationResponse;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;
import org.springframework.core.io.Resource;
import org.springframework.util.ObjectUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.utils.SdkAutoCloseable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Helper client that is able to serialize a Pojo using Avro.
 *
 * <p>This makes sure that the schema that was used to serialize this object via the {@link SchemaRegistryClient}.
 *
 * <p>Note that this was built for testing purposes in this library only and is not meant to be production quality.
 */
@Slf4j
public class AvroSchemaRegistrySqsAsyncClient implements SqsAsyncClient {
    @Delegate(excludes = SdkAutoCloseable.class)
    private final SqsAsyncClient delegate;
    private final SchemaRegistryClient schemaRegistryClient;
    private final AvroSchemaServiceManager avroSchemaServiceManager;
    private final Map<Class<?>, RegisteredSchema> schemaCache;

    public AvroSchemaRegistrySqsAsyncClient(final SqsAsyncClient delegate,
                                            final SchemaRegistryClient schemaRegistryClient,
                                            final AvroSchemaServiceManager avroSchemaServiceManager,
                                            final List<Resource> schemaImports,
                                            final List<Resource> schemaLocations) {
        this.delegate = delegate;
        this.schemaRegistryClient = schemaRegistryClient;
        this.avroSchemaServiceManager = avroSchemaServiceManager;
        this.schemaCache = parseAvailableSchemas(schemaImports, schemaLocations);
    }

    /**
     * Send a payload as a SQS message where it is serialized via the avro serialization process.
     *
     * <p>This uses message attributes as the content type carrier and the whole body is included in the payload of the message.
     *
     * @param contentTypePrefix    the custom prefix that could be included in the message
     * @param messageAttributeName the name of the attribute that will contain the content type
     * @param payload              the payload to send
     * @param requestBuilder       the builder that can be used to configure the request, such as configuring the URL, etc
     * @param <T>                  the type of the payload
     * @return the future containing the response for sending the message
     */
    public <T> CompletableFuture<SendMessageResponse> sendAvroMessage(final String contentTypePrefix,
                                                                      final String messageAttributeName,
                                                                      final T payload,
                                                                      final Consumer<SendMessageRequest.Builder> requestBuilder) {
        final RegisteredSchema registeredSchema = schemaCache.get(payload.getClass());
        final SchemaReference reference = registeredSchema.reference;

        return delegate.sendMessage(builder -> builder.applyMutation(requestBuilder)
                .messageAttributes(singletonMap(messageAttributeName, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(generateContentType(contentTypePrefix, reference))
                        .build()))
                .messageBody(serializeObject(payload, registeredSchema.schema)));
    }

    private Map<Class<?>, RegisteredSchema> parseAvailableSchemas(final List<Resource> schemaImports,
                                                                  final List<Resource> schemaLocations) {
        final Schema.Parser schemaParser = new Schema.Parser();
        return Stream.of(schemaImports, schemaLocations)
                .filter(arr -> !ObjectUtils.isEmpty(arr))
                .distinct()
                .flatMap(List::stream)
                .flatMap(resource -> {
                    final Schema schema;
                    try {
                        schema = schemaParser.parse(resource.getInputStream());
                    } catch (IOException ioException) {
                        throw new RuntimeException("Error parsing schema: " + resource.getFilename(), ioException);
                    }
                    if (schema.getType().equals(Schema.Type.UNION)) {
                        return schema.getTypes().stream();
                    } else {
                        return Stream.of(schema);
                    }
                })
                .map(this::createObjectMapping)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<Class<?>, RegisteredSchema> createObjectMapping(final Schema schema) {
        final SchemaRegistrationResponse response = this.schemaRegistryClient.register(
                schema.getName().toLowerCase(Locale.ENGLISH), AVRO_FORMAT, schema.toString()
        );
        log.info("Schema {} registered with id {}", schema.getName(), response.getId());
        final Class<?> clazz;
        try {
            clazz = Class.forName(schema.getNamespace() + "." + schema.getName());
        } catch (ClassNotFoundException classNotFoundException) {
            throw new RuntimeException(classNotFoundException);
        }

        return new AbstractMap.SimpleImmutableEntry<>(clazz, RegisteredSchema.builder()
                .schema(schema)
                .reference(response.getSchemaReference())
                .build());
    }

    private <T> String serializeObject(final T payload, final Schema schema) {
        final Class<?> clazz = payload.getClass();
        final DatumWriter<Object> writer = avroSchemaServiceManager.getDatumWriter(clazz, schema);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(baos, null);
        try {
            writer.write(payload, encoder);
            encoder.flush();
        } catch (IOException ioException) {
            throw new RuntimeException("Error serializing payload", ioException);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    @Value
    @Builder
    private static class RegisteredSchema {
        SchemaReference reference;
        Schema schema;
    }

    @Override
    public void close() {
        // Just needed cause lombok is odd for this class...
    }

    private String generateContentType(final String prefix, final SchemaReference reference) {
        return "application/" + prefix + "." + reference.getSubject() + ".v" + reference.getVersion() + "+" + reference.getFormat();
    }
}
