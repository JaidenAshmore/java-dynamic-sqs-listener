package it.com.jashmore.sqs.extensions.registry.avro.util;

import org.springframework.cloud.schema.registry.SchemaReference;
import org.springframework.cloud.schema.registry.SchemaRegistrationResponse;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySchemaRegistryClient implements SchemaRegistryClient {
    private final Map<SchemaReference, String> schemas = new ConcurrentHashMap<>();
    private int currentIdNumber = 1;

    @Override
    public SchemaRegistrationResponse register(final String subject, final String format, final String schema) {
        final SchemaReference schemaReference = new SchemaReference(subject, currentIdNumber, format);
        schemas.put(schemaReference, schema);
        final SchemaRegistrationResponse response = new SchemaRegistrationResponse();
        response.setSchemaReference(schemaReference);
        response.setId(currentIdNumber++);
        return response;
    }

    @Override
    public String fetch(final SchemaReference schemaReference) {
        return schemas.get(schemaReference);
    }

    @Override
    public String fetch(final int id) {
        throw new UnsupportedOperationException();
    }
}
