package com.jashmore.sqs.spring.decorator.visibilityextender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.AutoVisibilityExtenderMessageProcessingDecorator;
import com.jashmore.sqs.processor.DecoratingMessageProcessor;
import com.jashmore.sqs.processor.LambdaMessageProcessor;
import com.jashmore.sqs.spring.decorator.MessageProcessingDecoratorFactoryException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;

@ExtendWith(MockitoExtension.class)
class AutoVisibilityExtenderMessageProcessingDecoratorFactoryTest {
    @Mock
    SqsAsyncClient sqsAsyncClient;

    @Mock
    QueueProperties queueProperties;

    AutoVisibilityExtenderMessageProcessingDecoratorFactory factory = new AutoVisibilityExtenderMessageProcessingDecoratorFactory();

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
            .thenAnswer(
                invocation -> {
                    Consumer<ChangeMessageVisibilityBatchRequest.Builder> builder = invocation.getArgument(0);
                    final ChangeMessageVisibilityBatchRequest.Builder requestBuilder = ChangeMessageVisibilityBatchRequest.builder();
                    builder.accept(requestBuilder);
                    return CompletableFuture.completedFuture(ChangeMessageVisibilityBatchResponse.builder().build());
                }
            );

        // act
        messageProcessor
            .processMessage(Message.builder().receiptHandle("handle").build(), () -> CompletableFuture.completedFuture(null))
            .get(5, TimeUnit.SECONDS);

        // assert
        verify(sqsAsyncClient).changeMessageVisibilityBatch(ArgumentMatchers.<Consumer<ChangeMessageVisibilityBatchRequest.Builder>>any());
    }

    @AutoVisibilityExtender(visibilityTimeoutInSeconds = 2, maximumDurationInSeconds = 99, bufferTimeInSeconds = 1)
    public void methodWithAnnotation() {}

    public void methodWithNoAnnotation() {}

    @AutoVisibilityExtender(visibilityTimeoutInSeconds = 30, maximumDurationInSeconds = 100)
    public CompletableFuture<?> asyncMethodWithAnnotation() {
        return CompletableFuture.completedFuture(null);
    }
}
