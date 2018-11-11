package com.jashmore.sqs.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.jashmore.sqs.config.DynamicSqsListenerSpringStarterConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation used to enable any queue listeners marked in the application.
 */
@Retention(value = RUNTIME)
@Target(ElementType.TYPE)
@Import({DynamicSqsListenerSpringStarterConfiguration.class})
@Configuration
public @interface EnableQueueListeners {
}
