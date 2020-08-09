package com.jashmore.sqs.extensions.registry;

import static com.jashmore.sqs.extensions.registry.MessageAttributeSchemaReferenceExtractor.CONTENT_TYPE_MESSAGE_ATTRIBUTE_NAME;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.schema.registry.SchemaReference;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

class MessageAttributeSchemaReferenceExtractorTest {
    private final MessageAttributeSchemaReferenceExtractor messageAttributeSchemaReferenceExtractor = new MessageAttributeSchemaReferenceExtractor();

    @Test
    void noMessageAttributeWIthNameThrowsException() {
        // arrange
        final Message message = Message.builder().build();

        // act
        final SchemaReferenceExtractorException exception = assertThrows(
            SchemaReferenceExtractorException.class,
            () -> messageAttributeSchemaReferenceExtractor.extract(message)
        );

        // assert
        assertThat(exception).hasMessageContaining("No attribute found with name: contentType");
    }

    @Test
    void contentTypeValueThatIsNotStringThrowsException() {
        // arrange
        final Message message = Message
            .builder()
            .messageAttributes(
                singletonMap(
                    CONTENT_TYPE_MESSAGE_ATTRIBUTE_NAME,
                    MessageAttributeValue.builder().dataType("Binary").binaryValue(SdkBytes.fromByteArray(new byte[] { 0, 1 })).build()
                )
            )
            .build();

        // act
        final SchemaReferenceExtractorException exception = assertThrows(
            SchemaReferenceExtractorException.class,
            () -> messageAttributeSchemaReferenceExtractor.extract(message)
        );

        // assert
        assertThat(exception).hasMessageContaining("Attribute expected to be a String but is of type: Binary");
    }

    @Test
    void contentTypeThatIsUnsuccessfullyParsedThrowsException() {
        // arrange
        final Message message = Message
            .builder()
            .messageAttributes(
                singletonMap(
                    CONTENT_TYPE_MESSAGE_ATTRIBUTE_NAME,
                    MessageAttributeValue.builder().dataType("String").stringValue("invalid-format").build()
                )
            )
            .build();

        // act
        final SchemaReferenceExtractorException exception = assertThrows(
            SchemaReferenceExtractorException.class,
            () -> messageAttributeSchemaReferenceExtractor.extract(message)
        );

        // assert
        assertThat(exception).hasMessageContaining("Content type attribute value is not in the expected format: invalid-format");
    }

    @Test
    void contentTypeWithNonNumberVersionThrowsException() {
        // arrange
        final Message message = Message
            .builder()
            .messageAttributes(
                singletonMap(
                    CONTENT_TYPE_MESSAGE_ATTRIBUTE_NAME,
                    MessageAttributeValue.builder().dataType("String").stringValue("application/prefix.name.vOne+avro").build()
                )
            )
            .build();

        // act
        final SchemaReferenceExtractorException exception = assertThrows(
            SchemaReferenceExtractorException.class,
            () -> messageAttributeSchemaReferenceExtractor.extract(message)
        );

        // assert
        assertThat(exception)
            .hasMessageContaining("Content type attribute value is not in the expected format: application/prefix.name.vOne+avro");
    }

    @Test
    void contentTypeThatCanBeSuccessfullyParsedReturnsSchemaReference() {
        // arrange
        final Message message = Message
            .builder()
            .messageAttributes(
                singletonMap(
                    CONTENT_TYPE_MESSAGE_ATTRIBUTE_NAME,
                    MessageAttributeValue.builder().dataType("String").stringValue("application/prefix.name.v1+avro").build()
                )
            )
            .build();

        // act
        final SchemaReference schemaReference = messageAttributeSchemaReferenceExtractor.extract(message);

        // assert
        assertThat(schemaReference).isEqualTo(new SchemaReference("name", 1, "avro"));
    }
}
