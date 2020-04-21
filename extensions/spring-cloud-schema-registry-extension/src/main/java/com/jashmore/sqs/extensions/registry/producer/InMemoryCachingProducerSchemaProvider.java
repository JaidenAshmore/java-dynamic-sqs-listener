package com.jashmore.sqs.extensions.registry.producer;

import org.apache.avro.Schema;
import org.springframework.cloud.schema.registry.SchemaReference;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In memory cache implementation that can be used to reduce the number of times that the schema
 * is calculated (which would be a lot).
 *
 * <p>For example, if the {@link SchemaRegistryProducerSchemaProvider} was used as the delegate this would result
 * in only a single call made out to the remote service per schema instead of each time the message is being
 * deserialized.
 */
public class InMemoryCachingProducerSchemaProvider implements ProducerSchemaProvider {
    private final Map<SchemaReference, Schema> cache = new ConcurrentHashMap<>();
    private final ProducerSchemaProvider delegate;

    public InMemoryCachingProducerSchemaProvider(final ProducerSchemaProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Schema getSchema(final SchemaReference reference) {
        return cache.computeIfAbsent(reference, delegate::getSchema);
    }
}
