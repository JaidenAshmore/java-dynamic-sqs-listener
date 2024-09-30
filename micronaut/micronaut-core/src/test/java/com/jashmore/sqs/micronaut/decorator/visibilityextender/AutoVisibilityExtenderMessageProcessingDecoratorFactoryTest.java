package com.jashmore.sqs.micronaut.decorator.visibilityextender;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.AutoVisibilityExtenderMessageProcessingDecorator;
import com.jashmore.sqs.decorator.AutoVisibilityExtenderMessageProcessingDecoratorProperties;
import com.jashmore.sqs.micronaut.decorator.MessageProcessingDecoratorFactoryException;
import com.jashmore.sqs.processor.DecoratingMessageProcessor;
import com.jashmore.sqs.processor.LambdaMessageProcessor;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoVisibilityExtenderMessageProcessingDecoratorFactoryTest {

    @Mock
    SqsAsyncClient sqsAsyncClient;

    @Mock
    QueueProperties queueProperties;

    @Mock
    Environment environment;

    AutoVisibilityExtenderMessageProcessingDecoratorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AutoVisibilityExtenderMessageProcessingDecoratorFactory(environment);
    }

    @Test
    void willBuildDecoratorWhenAnnotationPresent() throws Exception {
        // arrange
        final Method method = AutoVisibilityExtenderMessageProcessingDecoratorFactoryTest.class.getMethod("methodWithAnnotation");

        // act
        final Optional<AutoVisibilityExtenderMessageProcessingDecorator> optionalDecorator = factory.buildDecorator(
            sqsAsyncClient,
            queueProperties,
            "id",
            this,
            method
        );

        // assert
        assertThat(optionalDecorator).isPresent();
    }

    @Test
    void willNotBuildDecoratorWhenAnnotationNotPresent() throws Exception {
        // arrange
        final Method method = AutoVisibilityExtenderMessageProcessingDecoratorFactoryTest.class.getMethod("methodWithNoAnnotation");

        // act
        final Optional<AutoVisibilityExtenderMessageProcessingDecorator> optionalDecorator = factory.buildDecorator(
            sqsAsyncClient,
            queueProperties,
            "id",
            this,
            method
        );

        // assert
        assertThat(optionalDecorator).isEmpty();
    }

    @Test
    void willThrowExceptionAttemptingToWrapAsyncMethod() throws Exception {
        // arrange
        final Method method = AutoVisibilityExtenderMessageProcessingDecoratorFactoryTest.class.getMethod("asyncMethodWithAnnotation");

        // act
        final MessageProcessingDecoratorFactoryException exception = Assertions.assertThrows(
            MessageProcessingDecoratorFactoryException.class,
            () -> factory.buildDecorator(sqsAsyncClient, queueProperties, "id", this, method)
        );

        // assert
        assertThat(exception)
            .hasMessage("AutoVisibilityExtenderMessageProcessingDecorator cannot be built around asynchronous message listeners");
    }

    @Test
    void builtDecoratorWillAutomaticallyExtendVisibility() throws Exception {
        // arrange
        final Method method = AutoVisibilityExtenderMessageProcessingDecoratorFactoryTest.class.getMethod("methodWithAnnotation");
        final AutoVisibilityExtenderMessageProcessingDecorator decorator = factory
            .buildDecorator(sqsAsyncClient, queueProperties, "id", this, method)
            .orElseThrow(() -> new RuntimeException("Error"));
        final DecoratingMessageProcessor messageProcessor = new DecoratingMessageProcessor(
            "id",
            queueProperties,
            Collections.singletonList(decorator),
            new LambdaMessageProcessor(
                sqsAsyncClient,
                queueProperties,
                message -> {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException interruptedException) {
                        // do nothing
                    }
                }
            )
        );
        when(sqsAsyncClient.changeMessageVisibilityBatch(ArgumentMatchers.<Consumer<ChangeMessageVisibilityBatchRequest.Builder>>any()))
            .thenAnswer(invocation -> {
                Consumer<ChangeMessageVisibilityBatchRequest.Builder> builder = invocation.getArgument(0);
                final ChangeMessageVisibilityBatchRequest.Builder requestBuilder = ChangeMessageVisibilityBatchRequest.builder();
                builder.accept(requestBuilder);
                return CompletableFuture.completedFuture(ChangeMessageVisibilityBatchResponse.builder().build());
            });

        // act
        messageProcessor
            .processMessage(Message.builder().receiptHandle("handle").build(), () -> CompletableFuture.completedFuture(null))
            .get(5, TimeUnit.SECONDS);

        // assert
        verify(sqsAsyncClient).changeMessageVisibilityBatch(ArgumentMatchers.<Consumer<ChangeMessageVisibilityBatchRequest.Builder>>any());
    }

    @Nested
    class BuildConfigurationProperties {

        @Test
        void shouldBeAbleToBuildPropertiesFromStrings() {
            // arrange
            when(environment.getPlaceholderResolver()).thenReturn(mock(PropertyPlaceholderResolver.class));
            when(environment.getPlaceholderResolver().resolveRequiredPlaceholders(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
            final AutoVisibilityExtender annotation = new AutoVisibilityExtender() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return AutoVisibilityExtender.class;
                }

                @Override
                public int visibilityTimeoutInSeconds() {
                    return -1;
                }

                @Override
                public String visibilityTimeoutInSecondsString() {
                    return "10";
                }

                @Override
                public int maximumDurationInSeconds() {
                    return -1;
                }

                @Override
                public String maximumDurationInSecondsString() {
                    return "20";
                }

                @Override
                public int bufferTimeInSeconds() {
                    return -1;
                }

                @Override
                public String bufferTimeInSecondsString() {
                    return "5";
                }
            };

            // act
            final AutoVisibilityExtenderMessageProcessingDecoratorProperties properties = factory.buildConfigurationProperties(annotation);

            // assert
            assertThat(properties.visibilityTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.maxDuration()).isEqualTo(Duration.ofSeconds(20));
            assertThat(properties.bufferDuration()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        void noVisibilityTimeoutWillThrowException() {
            // arrange
            final AutoVisibilityExtender annotation = new AutoVisibilityExtender() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return AutoVisibilityExtender.class;
                }

                @Override
                public int visibilityTimeoutInSeconds() {
                    return -1;
                }

                @Override
                public String visibilityTimeoutInSecondsString() {
                    return "";
                }

                @Override
                public int maximumDurationInSeconds() {
                    return -1;
                }

                @Override
                public String maximumDurationInSecondsString() {
                    return "20";
                }

                @Override
                public int bufferTimeInSeconds() {
                    return -1;
                }

                @Override
                public String bufferTimeInSecondsString() {
                    return "5";
                }
            };

            // act
            final IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> factory.buildConfigurationProperties(annotation)
            );

            // assert
            assertThat(exception).hasMessage("visibilityTimeoutInSeconds should be set/positive");
        }

        @Test
        void noMaximumDurationWillThrowException() {
            // arrange
            when(environment.getPlaceholderResolver()).thenReturn(mock(PropertyPlaceholderResolver.class));
            when(environment.getPlaceholderResolver().resolveRequiredPlaceholders(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
            final AutoVisibilityExtender annotation = new AutoVisibilityExtender() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return AutoVisibilityExtender.class;
                }

                @Override
                public int visibilityTimeoutInSeconds() {
                    return -1;
                }

                @Override
                public String visibilityTimeoutInSecondsString() {
                    return "5";
                }

                @Override
                public int maximumDurationInSeconds() {
                    return -1;
                }

                @Override
                public String maximumDurationInSecondsString() {
                    return "";
                }

                @Override
                public int bufferTimeInSeconds() {
                    return -1;
                }

                @Override
                public String bufferTimeInSecondsString() {
                    return "5";
                }
            };

            // act
            final IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> factory.buildConfigurationProperties(annotation)
            );

            // assert
            assertThat(exception).hasMessage("maximumDurationInSeconds should be set/positive");
        }

        @Test
        void noBufferTimeInSecondsWillThrowException() {
            // arrange
            when(environment.getPlaceholderResolver()).thenReturn(mock(PropertyPlaceholderResolver.class));
            when(environment.getPlaceholderResolver().resolveRequiredPlaceholders(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
            final AutoVisibilityExtender annotation = new AutoVisibilityExtender() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return AutoVisibilityExtender.class;
                }

                @Override
                public int visibilityTimeoutInSeconds() {
                    return -1;
                }

                @Override
                public String visibilityTimeoutInSecondsString() {
                    return "5";
                }

                @Override
                public int maximumDurationInSeconds() {
                    return -1;
                }

                @Override
                public String maximumDurationInSecondsString() {
                    return "20";
                }

                @Override
                public int bufferTimeInSeconds() {
                    return -1;
                }

                @Override
                public String bufferTimeInSecondsString() {
                    return "";
                }
            };

            // act
            final IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> factory.buildConfigurationProperties(annotation)
            );

            // assert
            assertThat(exception).hasMessage("bufferTimeInSeconds should be set/positive");
        }
    }

    @AutoVisibilityExtender(visibilityTimeoutInSeconds = 2, maximumDurationInSeconds = 99, bufferTimeInSeconds = 1)
    public void methodWithAnnotation() {}

    public void methodWithNoAnnotation() {}

    @AutoVisibilityExtender(visibilityTimeoutInSeconds = 30, maximumDurationInSeconds = 100, bufferTimeInSeconds = 1)
    public CompletableFuture<?> asyncMethodWithAnnotation() {
        return CompletableFuture.completedFuture(null);
    }
}
