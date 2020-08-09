package com.jashmore.sqs.extensions.registry;

import com.jashmore.documentation.annotations.ThreadSafe;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.Value;
import org.springframework.cloud.schema.registry.SchemaReference;
import org.springframework.cloud.schema.registry.SchemaRegistrationResponse;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;

/**
 * In Memory implementation of the {@link SchemaRegistryClient} that is used for mocking this service in Integration Tests.
 */
@ThreadSafe
public class InMemorySchemaRegistryClient implements SchemaRegistryClient {
    private final Map<SchemaReference, SchemaDetails> schemas = new ConcurrentHashMap<>();

    private final AtomicInteger schemaVersionNumber = new AtomicInteger(1);
    private final AtomicInteger schemaId = new AtomicInteger(1);

    @Override
    public SchemaRegistrationResponse register(final String subject, final String format, final String schema) {
        final SchemaReference schemaReference = new SchemaReference(subject, schemaVersionNumber.getAndIncrement(), format);
        final SchemaDetails schemaDetails = SchemaDetails.builder().id(schemaId.getAndIncrement()).schemaDefinition(schema).build();
        schemas.put(schemaReference, schemaDetails);
        final SchemaRegistrationResponse response = new SchemaRegistrationResponse();
        response.setSchemaReference(schemaReference);
        response.setId(schemaDetails.id);
        return response;
    }

    @Override
    public String fetch(final SchemaReference schemaReference) {
        return schemas.get(schemaReference).schemaDefinition;
    }

    @Override
    public String fetch(final int id) {
        return schemas
            .values()
            .stream()
            .filter(schemaDetails -> schemaDetails.id == id)
            .map(schemaDetails -> schemaDetails.schemaDefinition)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Could not find schema definition with ID " + id));
    }

    @Value
    @Builder
    private static class SchemaDetails {
        int id;
        String schemaDefinition;
    }
}
