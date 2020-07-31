package com.jashmore.sqs.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.argument.UnsupportedArgumentResolutionException;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@ExtendWith(MockitoExtension.class)
class CoreMessageProcessorTest {
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties
            .builder()
            .queueUrl("queueUrl")
            .build();
    private static final Message MESSAGE = Message.builder()
            .body("test")
            .build();
    private static final SynchronousMessageListenerScenarios syncMessageListener = new SynchronousMessageListenerScenarios();
    private static final Supplier<CompletableFuture<?>> NO_OP = () -> CompletableFuture.completedFuture(null);

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private ArgumentResolver<String> mockArgumentResolver;

    @Mock
    private Supplier<CompletableFuture<?>> mockMessageResolver;

    @Nested
    class Arguments {
        @Test
        void forEachParameterInMethodTheArgumentIsResolved() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithArguments", String.class, String.class);
            doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);

            // act
            processor.processMessage(MESSAGE, NO_OP);

            // assert
            verify(argumentResolverService, times(2)).getArgumentResolver(any(MethodParameter.class));
        }

        @Test
        void anyParameterUnableToBeResolvedWillThrowAnError() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithArguments", String.class, String.class);
            when(argumentResolverService.getArgumentResolver(any(MethodParameter.class)))
                    .thenThrow(new UnsupportedArgumentResolutionException());

            // act
            assertThrows(UnsupportedArgumentResolutionException.class,
                    () -> new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener));
        }

        @Test
        void methodWillBeInvokedWithArgumentsResolved() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithArguments", String.class, String.class);
            final SynchronousMessageListenerScenarios mockMessageListener = mock(SynchronousMessageListenerScenarios.class);
            doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
            when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(MESSAGE)))
                    .thenReturn("payload")
                    .thenReturn("payload2");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, mockMessageListener);

            // act
            processor.processMessage(MESSAGE, NO_OP);

            // assert
            verify(mockMessageListener).methodWithArguments("payload", "payload2");
        }

        @Test
        void methodWithVisibilityExtenderWillBeCorrectlyResolved() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithVisibilityExtender", VisibilityExtender.class);
            final SynchronousMessageListenerScenarios mockMessageListener = mock(SynchronousMessageListenerScenarios.class);
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, mockMessageListener);

            // act
            processor.processMessage(MESSAGE, NO_OP);

            // assert
            verify(mockMessageListener).methodWithVisibilityExtender(any(VisibilityExtender.class));
        }

        @Test
        void anyArgumentThatFailsToBeResolvedForMessageWillThrowMessageProcessingException() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithArguments", String.class, String.class);
            doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
            when(mockArgumentResolver.resolveArgumentForParameter(any(), any(), any()))
                    .thenThrow(new ExpectedTestException());
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);

            // act
            final ExecutionException exception = assertThrows(ExecutionException.class, () -> processor.processMessage(MESSAGE, NO_OP).get());

            // assert
            assertThat(exception).hasCauseInstanceOf(MessageProcessingException.class);
            assertThat(exception.getCause()).hasCauseInstanceOf(ExpectedTestException.class);
        }
    }

    @Nested
    class SynchronousMessageProcessing {

        @Test
        void willReturnCompletedFutureWhenTheMessageListenerDidNotThrowAnException() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);
            when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(result).isCompleted();
        }

        @Test
        void willAttemptToResolveMessageWhenMessageListenerProcessedSuccessfully() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);
            when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

            // act
            processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            verify(mockMessageResolver).get();
        }

        @Test
        void returnedCompletableFutureIsNotReliantOnMessageResolvingCompleting() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);
            when(mockMessageResolver.get()).thenReturn(new CompletableFuture<>());

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(result).isCompleted();
        }

        @Test
        void successfullyProcessingTheMessageWillAllowSubsequentFutureChainCallsToBeOnSameThread() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);
            when(mockMessageResolver.get()).thenReturn(new CompletableFuture<>());

            // act
            final AtomicReference<String> futureChainThreadName = new AtomicReference<>();
            final String messageListenerThreadName = Thread.currentThread().getName();
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver)
                    .thenAccept((ignored) -> futureChainThreadName.set(Thread.currentThread().getName()));

            // assert
            assertThat(result).isCompleted();
            assertThat(futureChainThreadName.get()).isEqualTo(messageListenerThreadName);
        }

        @Test
        void willReturnRejectedCompletableFutureWhenTheMessageListenerThrewAnException() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // arrange
            assertThat(result).isCompletedExceptionally();
        }

        @Test
        void unsuccessfullyProcessingTheMessageWillAllowSubsequentFutureChainCallsToBeOnSameThread() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);

            // act
            final AtomicReference<String> futureChainThreadName = new AtomicReference<>();
            final String messageListenerThreadName = Thread.currentThread().getName();
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver)
                    .whenComplete((ignored, throwable) -> futureChainThreadName.set(Thread.currentThread().getName()));

            // assert
            assertThat(result).isCompletedExceptionally();
            assertThat(futureChainThreadName.get()).isEqualTo(messageListenerThreadName);
        }

        @Test
        void failingToTriggerResolveMessageSupplierWillNotRejectFuture() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);
            when(mockMessageResolver.get()).thenThrow(ExpectedTestException.class);

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(result).isCompleted();
        }

        @Test
        void failingToResolveMessageWillNotRejectFuture() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);
            when(mockMessageResolver.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(result).isCompleted();
        }

        @Test
        void failingToProcessDueToIllegalAccessExceptionWilRejectFuture() {
            // arrange
            final Method method = SynchronousMessageListenerScenarios.getMethod("privateMethod");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, syncMessageListener);

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(result).isCompletedExceptionally();
            final ExecutionException processingException = assertThrows(ExecutionException.class, result::get);
            assertThat(processingException).hasCauseInstanceOf(MessageProcessingException.class);
            assertThat(processingException.getCause()).hasCauseInstanceOf(IllegalAccessException.class);
        }

        @Nested
        class AcknowledgeArgument {
            @Test
            void methodWithAcknowledgeParameterWillNotDeleteMessageOnSuccess() {
                // arrange
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithAcknowledge", Acknowledge.class);
                final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, syncMessageListener);

                // act
                processor.processMessage(MESSAGE, mockMessageResolver);

                // assert
                verify(mockMessageResolver, never()).get();
            }
        }
    }

    @Nested
    class AsynchronousMessageProcessing {
        private final AsynchronousMessageListenerScenarios asyncMessageListener = new AsynchronousMessageListenerScenarios();

        @Test
        void willReturnCompletedFutureWhenMessageListenerReturnsResolvedFuture() {
            // arrange
            final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturningResolvedFuture");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, asyncMessageListener);
            when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

            // act
            final CompletableFuture<?> completableFuture = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(completableFuture).isCompleted();
        }

        @Test
        void willAttemptToResolveMessageWhenMessageListenerReturnsCompletedFuture() {
            // arrange
            final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturningResolvedFuture");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, asyncMessageListener);
            when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

            // act
            final CompletableFuture<?> completableFuture = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(completableFuture).isCompleted();
            verify(mockMessageResolver).get();
        }

        @Test
        @SneakyThrows
        void whenTheMessageListenerReturnsCompletableFutureThatIsResolvedAsynchronouslyFutureChainIsNotOnSameThread() {
            // arrange
            final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, asyncMessageListener);
            when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
            final String messageListenerThreadName = Thread.currentThread().getName();

            // act
            final AtomicReference<String> futureChainThreadName = new AtomicReference<>();
            processor.processMessage(MESSAGE, mockMessageResolver)
                    .whenComplete((ignored, throwable) -> futureChainThreadName.set(Thread.currentThread().getName()))
                    .get(5, TimeUnit.SECONDS);

            // assert
            assertThat(futureChainThreadName).isNotNull();
            assertThat(futureChainThreadName.get()).isNotEqualTo(messageListenerThreadName);
        }

        @Test
        void willReturnRejectedFutureWhenMessageListenerThrowsException() {
            // arrange
            final Method method = AsynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, asyncMessageListener);

            // act
            final CompletableFuture<?> completableFuture = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(completableFuture).isCompletedExceptionally();
        }

        @Test
        void willNotAttemptToResolveMessageWhenMessageListenerThrowsException() {
            // arrange
            final Method method = AsynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, asyncMessageListener);

            // act
            final CompletableFuture<?> completableFuture = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(completableFuture).isCompletedExceptionally();
            verify(mockMessageResolver, never()).get();
        }

        @Test
        @SneakyThrows
        void whenTheMessageListenerReturnsCompletableFutureThatIsRejectedAsynchronouslyFutureChainIsNotOnSameThread() {
            // arrange
            final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, asyncMessageListener);
            when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
            final String messageListenerThreadName = Thread.currentThread().getName();

            // act
            final AtomicReference<String> futureChainThreadName = new AtomicReference<>();
            processor.processMessage(MESSAGE, mockMessageResolver)
                    .whenComplete((ignored, throwable) -> futureChainThreadName.set(Thread.currentThread().getName()))
                    .get(5, TimeUnit.SECONDS);

            // assert
            assertThat(futureChainThreadName).isNotNull();
            assertThat(futureChainThreadName.get()).isNotEqualTo(messageListenerThreadName);
        }

        @Test
        void messageListenerThatReturnsNullWillReturnRejectedFuture() {
            // arrange
            final Method method = AsynchronousMessageListenerScenarios.getMethod("methodThatReturnsNull");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, asyncMessageListener);

            // act
            final CompletableFuture<?> completableFuture = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(completableFuture).isCompletedExceptionally();
        }

        @Test
        void thatCompletesButMessageResolvingFailsWillNotRejectFuture() {
            // arrange
            final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturningResolvedFuture");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, asyncMessageListener);
            when(mockMessageResolver.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));

            // act
            final CompletableFuture<?> completableFuture = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(completableFuture).isCompleted();
        }
    }
}
