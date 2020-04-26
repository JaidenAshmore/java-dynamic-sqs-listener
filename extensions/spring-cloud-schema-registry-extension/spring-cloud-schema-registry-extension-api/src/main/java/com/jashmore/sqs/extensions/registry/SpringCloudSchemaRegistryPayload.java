package com.jashmore.sqs.extensions.registry;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation for a message consumer's parameter that indicate that it should be resolved to the payload of the message using schemas defined
 * in the Spring Cloud Schema Registry.
 *
 * <p>Parameters marked with this annotation do not have a type restriction but the type of the parameter must be able to be map
 * from the message body via the Schema, such as an Avro schema.
 */
@Retention(value = RUNTIME)
@Target(ElementType.PARAMETER)
public @interface SpringCloudSchemaRegistryPayload {
}
