package com.jashmore.sqs.extensions.registry;

/**
 * Exception thrown when there was an error trying to obtain the schema of the message that the producer
 * used to publish the message.
 */
public class ProducerSchemaRetrieverException extends RuntimeException {

    public ProducerSchemaRetrieverException(final Throwable cause) {
        super(cause);
    }
}
