package com.jashmore.sqs.extensions.registry;

import com.jashmore.documentation.annotations.ThreadSafe;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cloud.schema.registry.SchemaReference;

/**
 * In memory cache implementation that can be used to reduce the number of times that the schema
 * is calculated (which would be a lot).
 */
@ThreadSafe
public class InMemoryCachingProducerSchemaRetriever<T> implements ProducerSchemaRetriever<T> {

    private final Map<SchemaReference, T> cache = new ConcurrentHashMap<>();
    private final ProducerSchemaRetriever<T> delegate;

    public InMemoryCachingProducerSchemaRetriever(final ProducerSchemaRetriever<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T getSchema(final SchemaReference reference) {
        return cache.computeIfAbsent(reference, delegate::getSchema);
    }
}
