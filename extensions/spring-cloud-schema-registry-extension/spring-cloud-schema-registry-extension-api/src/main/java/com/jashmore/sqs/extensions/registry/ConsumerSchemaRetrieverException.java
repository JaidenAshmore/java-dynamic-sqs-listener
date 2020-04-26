package com.jashmore.sqs.extensions.registry;

/**
 * Exception thrown if there is a problem getting the schema that the consumer has knowledge of.
 */
public class ConsumerSchemaRetrieverException extends RuntimeException {

    public ConsumerSchemaRetrieverException(final String message) {
        super(message);
    }
}
