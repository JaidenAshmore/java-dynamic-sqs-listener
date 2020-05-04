package com.jashmore.sqs.extensions.registry;

/**
 * Exception thrown if there is a problem getting the consumer schema.
 */
public class ConsumerSchemaRetrieverException extends RuntimeException {

    public ConsumerSchemaRetrieverException(final String message) {
        super(message);
    }
}
