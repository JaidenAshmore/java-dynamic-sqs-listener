package com.jashmore.sqs.extensions.registry.consumer;


import static java.util.stream.Collectors.toMap;

import org.apache.avro.Schema;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Implementation that loads all of the schema definitions from resources in the classpath.
 *
 * <p>For example it will load all of the schemas in a folder like <pre>resources/avro/{name}.avsc</pre>.
 */
public class ClasspathConsumerSchemaProvider implements ConsumerSchemaProvider {
    private final Map<Class<?>, Schema> classSchemaMap;

    public ClasspathConsumerSchemaProvider(final Resource[] schemaLocations) {
        classSchemaMap = Stream.of(schemaLocations)
                .map(resource -> {
                    try {
                        return new Schema.Parser().parse(resource.getInputStream());
                    } catch (IOException e) {
                        throw new RuntimeException("Error processing Schema definitions");
                    }
                })
                .collect(toMap(this::getClassForSchema, Function.identity()));
    }


    @Override
    public Schema get(final Class<?> aClass) {
        return classSchemaMap.get(aClass);
    }

    private Class<?> getClassForSchema(final Schema schema) {
        try {
            return Class.forName(schema.getNamespace() + "." + schema.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find class for schema");
        }
    }
}
