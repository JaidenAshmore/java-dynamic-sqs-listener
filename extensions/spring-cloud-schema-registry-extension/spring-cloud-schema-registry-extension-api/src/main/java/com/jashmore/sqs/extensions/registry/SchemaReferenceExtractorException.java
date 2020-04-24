package com.jashmore.sqs.extensions.registry;

/**
 * Exception for when there was a problem determining the version of the schema for the recieved message.
 */
public class SchemaReferenceExtractorException extends RuntimeException {
    public SchemaReferenceExtractorException(final String message) {
        super(message);
    }

    public SchemaReferenceExtractorException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
