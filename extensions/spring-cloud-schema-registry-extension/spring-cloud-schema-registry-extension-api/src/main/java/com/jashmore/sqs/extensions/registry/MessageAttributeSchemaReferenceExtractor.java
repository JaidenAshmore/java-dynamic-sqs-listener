package com.jashmore.sqs.extensions.registry;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.cloud.schema.registry.SchemaReference;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * Obtains the MimeType that contains information about the schema of the payload ({@link SchemaReference}) by looking
 * in the message attribute of the message.
 */
public class MessageAttributeSchemaReferenceExtractor implements SchemaReferenceExtractor {
    private static final Pattern MIME_TYPE_PATTERN = Pattern.compile(
        "application/([^.]+)\\.([\\p{Alnum}\\$\\.]+)\\.v(\\p{Digit}+)\\+([^.]+)"
    );
    public static final String CONTENT_TYPE_MESSAGE_ATTRIBUTE_NAME = "contentType";

    private final String attributeName;

    public MessageAttributeSchemaReferenceExtractor() {
        this(CONTENT_TYPE_MESSAGE_ATTRIBUTE_NAME);
    }

    /**
     * Create the extractor using the provided Message Attribute name.
     *
     * @param attributeName the attribute name in the SQS message that will contain the content type
     */
    public MessageAttributeSchemaReferenceExtractor(final String attributeName) {
        this.attributeName = attributeName;
    }

    @Override
    public SchemaReference extract(final Message message) {
        final MessageAttributeValue contentTypeAttribute = message.messageAttributes().get(attributeName);
        if (contentTypeAttribute == null) {
            throw new SchemaReferenceExtractorException("No attribute found with name: " + attributeName);
        }

        if (!contentTypeAttribute.dataType().equals("String")) {
            throw new SchemaReferenceExtractorException(
                "Attribute expected to be a String but is of type: " + contentTypeAttribute.dataType()
            );
        }

        final Matcher matcher = MIME_TYPE_PATTERN.matcher(contentTypeAttribute.stringValue());
        if (!matcher.matches()) {
            throw new SchemaReferenceExtractorException(
                "Content type attribute value is not in the expected format: " + contentTypeAttribute.stringValue()
            );
        }

        final String subject = matcher.group(2);
        final String version = matcher.group(3);
        final String format = matcher.group(4);
        return new SchemaReference(subject, Integer.parseInt(version), format);
    }
}
