package com.jashmore.sqs.spring.decorator.visibilityextender;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(METHOD)
public @interface AutoVisibilityExtender {
    int visibilityTimeoutInSeconds();

    int maximumDurationInSeconds();

    int bufferTimeInSeconds() default 2;
}
