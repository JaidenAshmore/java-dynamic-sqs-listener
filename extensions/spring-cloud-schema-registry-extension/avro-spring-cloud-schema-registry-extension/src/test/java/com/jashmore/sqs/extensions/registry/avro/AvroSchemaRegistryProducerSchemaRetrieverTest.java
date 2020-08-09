package com.jashmore.sqs.extensions.registry.avro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.extensions.registry.ProducerSchemaRetrieverException;
import java.io.IOException;
import org.apache.avro.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.schema.registry.SchemaReference;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
class AvroSchemaRegistryProducerSchemaRetrieverTest {
    private static final Schema.Parser SCHEMA_PARSER = new Schema.Parser();
    private static final Schema SCHEMA;

    @Mock
    private SchemaRegistryClient schemaRegistryClient;

    private AvroSchemaRegistryProducerSchemaRetriever avroSchemaRegistryProducerSchemaRetriever;

    static {
        final ClassPathResource resource = new ClassPathResource("avro-test-schemas/import/author.avsc");
        try {
            SCHEMA = SCHEMA_PARSER.parse(resource.getInputStream());
        } catch (IOException ioException) {
            throw new RuntimeException("Error parsing schema: " + resource.getFilename(), ioException);
        }
    }

    @BeforeEach
    void setUp() {
        avroSchemaRegistryProducerSchemaRetriever = new AvroSchemaRegistryProducerSchemaRetriever(schemaRegistryClient);
    }

    @Test
    void canObtainSchemaFromRegistry() {
        // arrange
        final SchemaReference schemaReference = new SchemaReference("subject", 1, "format");
        when(schemaRegistryClient.fetch(schemaReference)).thenReturn(SCHEMA.toString());

        // act
        final Schema schema = avroSchemaRegistryProducerSchemaRetriever.getSchema(schemaReference);

        // assert
        assertThat(schema).isEqualTo(SCHEMA);
    }

    @Test
    void errorParsingSchemaWillThrowException() {
        // arrange
        final SchemaReference schemaReference = new SchemaReference("subject", 1, "format");
        when(schemaRegistryClient.fetch(schemaReference)).thenReturn("invalid Avro schema");

        // act
        assertThrows(ProducerSchemaRetrieverException.class, () -> avroSchemaRegistryProducerSchemaRetriever.getSchema(schemaReference));
    }
}
