package com.jashmore.sqs.extensions.registry.avro;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.jashmore.sqs.extensions.registry.SpringCloudSchemaSqsConfiguration;
import com.jashmore.sqs.extensions.registry.avro.AvroSqsSpringCloudSchemaRegistryConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(value = RUNTIME)
@Target(ElementType.TYPE)
@Import({ SpringCloudSchemaSqsConfiguration.class, AvroSqsSpringCloudSchemaRegistryConfiguration.class })
public @interface EnableSchemaRegistrySqsExtension {
}
