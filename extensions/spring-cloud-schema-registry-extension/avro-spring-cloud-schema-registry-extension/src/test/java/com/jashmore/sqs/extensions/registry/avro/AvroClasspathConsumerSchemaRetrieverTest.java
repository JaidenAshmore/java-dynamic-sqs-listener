package com.jashmore.sqs.extensions.registry.avro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.extensions.registry.ConsumerSchemaRetrieverException;
import com.jashmore.sqs.extensions.registry.model.Author;
import com.jashmore.sqs.extensions.registry.model.Book;
import org.apache.avro.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AvroClasspathConsumerSchemaRetrieverTest {
    @Nested
    class SchemaParsing {
        @Test
        void creatingSchemaForResourceThatDoesNotExistThrowsException() {
            final AvroSchemaProcessingException exception = assertThrows(AvroSchemaProcessingException.class, () -> new AvroClasspathConsumerSchemaRetriever(
                    ImmutableList.of(new ClassPathResource("unknown/schema.avsc")),
                    ImmutableList.of()
            ));

            assertThat(exception).hasMessage("Error processing schema definition: schema.avsc");
        }

        @Test
        void schemaThatHasNotHadTheJavaFileGeneratedWillReturnError() {
            final AvroSchemaProcessingException exception = assertThrows(AvroSchemaProcessingException.class, () -> new AvroClasspathConsumerSchemaRetriever(
                    ImmutableList.of(new ClassPathResource("avro-non-generated-test-schemas/non-built-schema.avsc")),
                    ImmutableList.of()
            ));

            assertThat(exception).hasMessage("Could not find class for schema: com.jashmore.sqs.extensions.registry.model.NonBuiltSchema");
        }

        @Test
        void duplicateSchemaDefinitionsAreIgnored() {
            new AvroClasspathConsumerSchemaRetriever(
                    ImmutableList.of(
                            new ClassPathResource("avro-test-schemas/import/author.avsc"), new ClassPathResource("avro-test-schemas/import/author.avsc")
                    ),
                    ImmutableList.of(
                            new ClassPathResource("avro-test-schemas/schema/book.avsc"), new ClassPathResource("avro-test-schemas/schema/book.avsc")
                    )
            );
        }

        @Test
        void missingDependentSchemasWillThrowExceptionInParsing() {
            final AvroSchemaProcessingException exception = assertThrows(AvroSchemaProcessingException.class,() -> new AvroClasspathConsumerSchemaRetriever(
                    ImmutableList.of(),
                    ImmutableList.of(new ClassPathResource("avro-test-schemas/schema/book.avsc"))
            ));

            assertThat(exception).hasMessage("Error processing schema definition: book.avsc");
        }
    }

    @Nested
    class GetSchema {
        private AvroClasspathConsumerSchemaRetriever avroClasspathConsumerSchemaRetriever;

        @BeforeEach
        void setUp() {
            avroClasspathConsumerSchemaRetriever = new AvroClasspathConsumerSchemaRetriever(
                    ImmutableList.of(new ClassPathResource("avro-test-schemas/import/author.avsc")),
                    ImmutableList.of(new ClassPathResource("avro-test-schemas/schema/book.avsc"))
            );
        }

        @Test
        void canObtainSchemaForLeafSchema() {
            final Schema schema = avroClasspathConsumerSchemaRetriever.getSchema(Author.class);

            assertThat(schema).isNotNull();
        }

        @Test
        void canObtainSchemaForSchemaWithChildrenSchema() {
            final Schema schema = avroClasspathConsumerSchemaRetriever.getSchema(Book.class);

            assertThat(schema).isNotNull();
        }

        @Test
        void obtainingSchemaForClassThatDoesNotExistThrowsException() {
            assertThrows(ConsumerSchemaRetrieverException.class, () -> avroClasspathConsumerSchemaRetriever.getSchema(String.class));
        }
    }
}