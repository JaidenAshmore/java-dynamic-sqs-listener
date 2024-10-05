package com.jashmore.sqs.extensions.registry.avro;

import static java.util.stream.Collectors.toMap;

import com.jashmore.sqs.extensions.registry.ConsumerSchemaRetriever;
import com.jashmore.sqs.extensions.registry.ConsumerSchemaRetrieverException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.springframework.core.io.Resource;

/**
 * Implementation that loads all the schema definitions from resources in the classpath.
 *
 * <p>For example it will load all the schemas in a folder like <pre>resources/avro/{name}.avsc</pre>.
 */
public class AvroClasspathConsumerSchemaRetriever implements ConsumerSchemaRetriever<Schema> {

    private final Map<Class<?>, Schema> classSchemaMap;

    public AvroClasspathConsumerSchemaRetriever(final List<Resource> schemaImports, final List<Resource> schemaLocations) {
        final Schema.Parser parser = new Schema.Parser();
        classSchemaMap =
            Stream
                .of(schemaImports, schemaLocations)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .map(resource -> {
                    try {
                        return parser.parse(resource.getInputStream());
                    } catch (AvroTypeException | SchemaParseException | IOException exception) {
                        throw new AvroSchemaProcessingException("Error processing schema definition: " + resource.getFilename(), exception);
                    }
                })
                .collect(toMap(this::getClassForSchema, Function.identity()));
    }

    @Override
    public Schema getSchema(final Class<?> clazz) {
        return Optional
            .ofNullable(classSchemaMap.get(clazz))
            .orElseThrow(() -> new ConsumerSchemaRetrieverException("Could not schema for class: " + clazz.getName()));
    }

    private Class<?> getClassForSchema(final Schema schema) {
        final String schemaClassName = schema.getNamespace() + "." + schema.getName();
        try {
            return Class.forName(schemaClassName);
        } catch (ClassNotFoundException classNotFoundException) {
            throw new AvroSchemaProcessingException("Could not find class for schema: " + schemaClassName, classNotFoundException);
        }
    }
}
