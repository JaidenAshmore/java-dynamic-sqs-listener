package com.jashmore.sqs.extensions.registry.avro;

import static java.util.stream.Collectors.toMap;

import com.jashmore.sqs.extensions.registry.ConsumerSchemaRetriever;
import org.apache.avro.Schema;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Implementation that loads all of the schema definitions from resources in the classpath.
 *
 * <p>For example it will load all of the schemas in a folder like <pre>resources/avro/{name}.avsc</pre>.
 */
public class AvroClasspathConsumerSchemaRetriever implements ConsumerSchemaRetriever<Schema> {
    private final Map<Class<?>, Schema> classSchemaMap;

    public AvroClasspathConsumerSchemaRetriever(final List<Resource> schemaImports,
                                                final List<Resource> schemaLocations) {
        classSchemaMap = Stream.of(schemaImports, schemaLocations)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .map(resource -> {
                    try {
                        return new Schema.Parser().parse(resource.getInputStream());
                    } catch (IOException ioException) {
                        throw new RuntimeException("Error processing Schema definitions", ioException);
                    }
                })
                .collect(toMap(this::getClassForSchema, Function.identity()));
    }


    @Override
    public Schema getSchema(final Class<?> clazz) {
        return classSchemaMap.get(clazz);
    }

    private Class<?> getClassForSchema(final Schema schema) {
        try {
            return Class.forName(schema.getNamespace() + "." + schema.getName());
        } catch (ClassNotFoundException classNotFoundException) {
            throw new RuntimeException("Could not find class for schema", classNotFoundException);
        }
    }
}
