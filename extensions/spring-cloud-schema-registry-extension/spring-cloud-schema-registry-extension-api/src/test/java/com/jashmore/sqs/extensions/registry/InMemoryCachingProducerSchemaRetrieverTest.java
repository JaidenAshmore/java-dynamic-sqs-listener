package com.jashmore.sqs.extensions.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.schema.registry.SchemaReference;

@ExtendWith(MockitoExtension.class)
class InMemoryCachingProducerSchemaRetrieverTest {
    @Mock
    private ProducerSchemaRetriever<String> mockDelegate;

    private InMemoryCachingProducerSchemaRetriever<String> producerSchemaRetriever;

    @BeforeEach
    void setUp() {
        producerSchemaRetriever = new InMemoryCachingProducerSchemaRetriever<>(mockDelegate);
    }

    @Test
    void cachesSubsequentCallsForTheSameSchema() {
        // arrange
        final SchemaReference reference = new SchemaReference("subject", 1, "avro");
        when(mockDelegate.getSchema(reference)).thenReturn("schema");

        // act
        final String schema = producerSchemaRetriever.getSchema(reference);
        final String secondSchema = producerSchemaRetriever.getSchema(reference);

        // assert
        assertThat(schema).isSameAs(secondSchema);
        verify(mockDelegate, times(1)).getSchema(reference);
    }

    @Test
    void cachesAreAppliedPerReferenceValuesNotByInstance() {
        // arrange
        final SchemaReference reference = new SchemaReference("subject", 1, "avro");
        final SchemaReference anotherReference = new SchemaReference("subject", 1, "avro");
        when(mockDelegate.getSchema(any())).thenReturn("schema");

        // act
        final String schema = producerSchemaRetriever.getSchema(reference);
        final String secondSchema = producerSchemaRetriever.getSchema(anotherReference);

        // assert
        assertThat(schema).isSameAs(secondSchema);
        verify(mockDelegate, times(1)).getSchema(reference);
    }

    @Test
    void differentSchemaReferencesWillCacheDifferentValues() {
        // arrange
        final SchemaReference reference = new SchemaReference("subject", 1, "avro");
        final SchemaReference secondReference = new SchemaReference("subject", 2, "avro");
        when(mockDelegate.getSchema(reference)).thenReturn("schema");
        when(mockDelegate.getSchema(secondReference)).thenReturn("secondSchema");

        // act
        producerSchemaRetriever.getSchema(reference);
        final String schema = producerSchemaRetriever.getSchema(reference);
        producerSchemaRetriever.getSchema(secondReference);
        final String secondSchema = producerSchemaRetriever.getSchema(secondReference);

        // assert
        assertThat(schema).isEqualTo("schema");
        assertThat(secondSchema).isEqualTo("secondSchema");
        verify(mockDelegate, times(1)).getSchema(reference);
        verify(mockDelegate, times(1)).getSchema(secondReference);
    }
}
