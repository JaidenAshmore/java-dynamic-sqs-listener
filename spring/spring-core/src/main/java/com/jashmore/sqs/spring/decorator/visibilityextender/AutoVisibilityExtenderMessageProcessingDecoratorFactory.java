package com.jashmore.sqs.spring.decorator.visibilityextender;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.AutoVisibilityExtenderMessageProcessingDecorator;
import com.jashmore.sqs.decorator.AutoVisibilityExtenderMessageProcessingDecoratorProperties;
import com.jashmore.sqs.spring.decorator.MessageProcessingDecoratorFactory;
import com.jashmore.sqs.spring.decorator.MessageProcessingDecoratorFactoryException;
import com.jashmore.sqs.util.annotation.AnnotationUtils;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Factory used to wrap any message listeners with an {@link AutoVisibilityExtender @AutoVisibilityExtender} annotation with a
 * {@link AutoVisibilityExtenderMessageProcessingDecorator}.
 */
public class AutoVisibilityExtenderMessageProcessingDecoratorFactory
    implements MessageProcessingDecoratorFactory<AutoVisibilityExtenderMessageProcessingDecorator> {

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

    private AutoVisibilityExtenderMessageProcessingDecoratorProperties buildConfigurationProperties(
        final AutoVisibilityExtender annotation
    ) {
        return new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {

            @Override
            public Duration visibilityTimeout() {
                return Duration.ofSeconds(annotation.visibilityTimeoutInSeconds());
            }

            @Override
            public Duration maxDuration() {
                return Duration.ofSeconds(annotation.maximumDurationInSeconds());
            }

            @Override
            public Duration bufferDuration() {
                return Duration.ofSeconds(annotation.bufferTimeInSeconds());
            }
        };
    }
}
