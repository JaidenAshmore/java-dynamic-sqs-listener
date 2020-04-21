package com.jashmore.sqs.extensions.registry.schemareference;

import static org.springframework.cloud.schema.registry.avro.AvroSchemaRegistryClientMessageConverter.AVRO_FORMAT;

import org.springframework.cloud.schema.registry.SchemaReference;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Obtains the MimeType that contains information about the schema of the payload ({@link SchemaReference}) by looking
 * in the message attribute of the message.
 */
public class MessageAttributeSchemaReferenceExtractor implements SchemaReferenceExtractor {
    private static final Pattern MIME_TYPE_PATTERN = Pattern.compile("application/([^.]+)\\.([^.]+)\\.v(\\d+)\\+avro");
    private static final String CONTENT_TYPE_MESSAGE_ATTRIBUTE_NAME = "contentType";

    private final String attributeName;

    public MessageAttributeSchemaReferenceExtractor() {
        this(CONTENT_TYPE_MESSAGE_ATTRIBUTE_NAME);
    }

    public MessageAttributeSchemaReferenceExtractor(final String attributeName) {
        this.attributeName = attributeName;
    }

    @Override
    public SchemaReference extract(final Message message) {
        final MessageAttributeValue contentTypeAttribute = message.messageAttributes().get(attributeName);
        if (contentTypeAttribute == null) {
            throw new SchemaReferenceExtractorException("No Content Type present in message");
        }

        final Matcher matcher = MIME_TYPE_PATTERN.matcher(contentTypeAttribute.stringValue());
        if (!matcher.matches()) {
            throw new SchemaReferenceExtractorException("Could not parse ContentType");
        }

        final String subject = matcher.group(2);
        final String version = matcher.group(3);
        try {
            return new SchemaReference(subject, Integer.parseInt(version), AVRO_FORMAT);
        } catch (NumberFormatException e) {
            throw new SchemaReferenceExtractorException("Version for the schema is not in a number format", e);
        }
    }
}
