package com.jashmore.sqs.extensions.registry.producer;

/**
 * Exception thrown when there was an error trying to obtain the schema of the message that the producer
 * used to publish the message.
 */
public class ProducerSchemaRetrievalException extends RuntimeException {
    public ProducerSchemaRetrievalException(final Throwable cause) {
        super(cause);
    }
}
