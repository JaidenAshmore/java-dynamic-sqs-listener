package com.jashmore.sqs.extensions.registry.config;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(value = RUNTIME)
@Target(ElementType.TYPE)
@Import(SpringCloudSchemaSqsConfiguration.class)
public @interface EnableSchemaRegistrySqsExtension {
}
