package com.jashmore.sqs.micronaut.decorator.visibilityextender;

import com.jashmore.documentation.annotations.VisibleForTesting;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.AutoVisibilityExtenderMessageProcessingDecorator;
import com.jashmore.sqs.decorator.AutoVisibilityExtenderMessageProcessingDecoratorProperties;
import com.jashmore.sqs.micronaut.decorator.MessageProcessingDecoratorFactory;
import com.jashmore.sqs.micronaut.decorator.MessageProcessingDecoratorFactoryException;
import com.jashmore.sqs.util.annotation.AnnotationUtils;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Factory used to wrap any message listeners with an {@link AutoVisibilityExtender @AutoVisibilityExtender} annotation with a
 * {@link AutoVisibilityExtenderMessageProcessingDecorator}.
 */
public class AutoVisibilityExtenderMessageProcessingDecoratorFactory
    implements MessageProcessingDecoratorFactory<AutoVisibilityExtenderMessageProcessingDecorator> {

    private final Environment environment;

    public AutoVisibilityExtenderMessageProcessingDecoratorFactory(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<AutoVisibilityExtenderMessageProcessingDecorator> buildDecorator(
        final SqsAsyncClient sqsAsyncClient,
        final QueueProperties queueProperties,
        final String identifier,
        final Object bean,
        final Method method
    ) {
        final Optional<AutoVisibilityExtender> optionalAnnotation = AnnotationUtils.findMethodAnnotation(
            method,
            AutoVisibilityExtender.class
        );

        if (!optionalAnnotation.isPresent()) {
            return Optional.empty();
        }

        if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
            throw new MessageProcessingDecoratorFactoryException(
                AutoVisibilityExtenderMessageProcessingDecorator.class.getSimpleName() +
                " cannot be built around asynchronous message listeners"
            );
        }

        return optionalAnnotation
            .map(this::buildConfigurationProperties)
            .map(properties -> new AutoVisibilityExtenderMessageProcessingDecorator(sqsAsyncClient, queueProperties, properties));
    }

    @VisibleForTesting
    AutoVisibilityExtenderMessageProcessingDecoratorProperties buildConfigurationProperties(final AutoVisibilityExtender annotation) {
        final Duration visibilityTimeout = getMessageVisibilityTimeout(annotation);
        final Duration maximumDuration = getMaximumDuration(annotation);
        final Duration bufferDuration = getBufferDuration(annotation);
        return new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {
            @Override
            public Duration visibilityTimeout() {
                return visibilityTimeout;
            }

            @Override
            public Duration maxDuration() {
                return maximumDuration;
            }

            @Override
            public Duration bufferDuration() {
                return bufferDuration;
            }
        };
    }

    private Duration getMessageVisibilityTimeout(final AutoVisibilityExtender annotation) {
        return getDurationFromSeconds(
            "visibilityTimeoutInSeconds",
            annotation::visibilityTimeoutInSecondsString,
            annotation::visibilityTimeoutInSeconds
        );
    }

    private Duration getMaximumDuration(final AutoVisibilityExtender annotation) {
        return getDurationFromSeconds(
            "maximumDurationInSeconds",
            annotation::maximumDurationInSecondsString,
            annotation::maximumDurationInSeconds
        );
    }

    private Duration getBufferDuration(final AutoVisibilityExtender annotation) {
        return getDurationFromSeconds("bufferTimeInSeconds", annotation::bufferTimeInSecondsString, annotation::bufferTimeInSeconds);
    }

    private Duration getDurationFromSeconds(
        final String propertyName,
        final Supplier<String> stringProperty,
        final Supplier<Integer> integerSupplier
    ) {
        final String stringPropertyValue = stringProperty.get();
        if (StringUtils.hasText(stringPropertyValue)) {
            return Duration.ofSeconds(Integer.parseInt(environment.getPlaceholderResolver()
                    .resolveRequiredPlaceholders(stringPropertyValue)));
        }

        final Integer integerValue = integerSupplier.get();
        if (integerValue == null || integerValue <= 0) {
            throw new IllegalArgumentException(propertyName + " should be set/positive");
        }

        return Duration.ofSeconds(integerValue);
    }
}
