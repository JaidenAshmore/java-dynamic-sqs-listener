package com.jashmore.sqs.extensions.registry.avro;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jashmore.sqs.extensions.registry.ConsumerSchemaRetrieverException;
import com.jashmore.sqs.extensions.registry.model.Author;
import com.jashmore.sqs.extensions.registry.model.Book;
import java.util.Arrays;
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
            final AvroSchemaProcessingException exception = assertThrows(
                AvroSchemaProcessingException.class,
                () -> new AvroClasspathConsumerSchemaRetriever(singletonList(new ClassPathResource("unknown/schema.avsc")), emptyList())
            );

            assertThat(exception).hasMessage("Error processing schema definition: schema.avsc");
        }

        @Test
        void schemaThatHasNotHadTheJavaFileGeneratedWillReturnError() {
            final AvroSchemaProcessingException exception = assertThrows(
                AvroSchemaProcessingException.class,
                () ->
                    new AvroClasspathConsumerSchemaRetriever(
                        singletonList(new ClassPathResource("avro-non-generated-test-schemas/non-built-schema.avsc")),
                        emptyList()
                    )
            );

            assertThat(exception).hasMessage("Could not find class for schema: com.jashmore.sqs.extensions.registry.model.NonBuiltSchema");
        }

        @Test
        void duplicateSchemaDefinitionsAreIgnored() {
            new AvroClasspathConsumerSchemaRetriever(
                Arrays.asList(
                    new ClassPathResource("avro-test-schemas/import/author.avsc"),
                    new ClassPathResource("avro-test-schemas/import/author.avsc")
                ),
                Arrays.asList(
                    new ClassPathResource("avro-test-schemas/schema/book.avsc"),
                    new ClassPathResource("avro-test-schemas/schema/book.avsc")
                )
            );
        }

        @Test
        void missingDependentSchemasWillThrowExceptionInParsing() {
            final AvroSchemaProcessingException exception = assertThrows(
                AvroSchemaProcessingException.class,
                () ->
                    new AvroClasspathConsumerSchemaRetriever(
                        emptyList(),
                        singletonList(new ClassPathResource("avro-test-schemas/schema/book.avsc"))
                    )
            );

            assertThat(exception).hasMessage("Error processing schema definition: book.avsc");
        }
    }

    @Nested
    class GetSchema {
        private AvroClasspathConsumerSchemaRetriever avroClasspathConsumerSchemaRetriever;

        @BeforeEach
        void setUp() {
            avroClasspathConsumerSchemaRetriever =
                new AvroClasspathConsumerSchemaRetriever(
                    singletonList(new ClassPathResource("avro-test-schemas/import/author.avsc")),
                    singletonList(new ClassPathResource("avro-test-schemas/schema/book.avsc"))
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
