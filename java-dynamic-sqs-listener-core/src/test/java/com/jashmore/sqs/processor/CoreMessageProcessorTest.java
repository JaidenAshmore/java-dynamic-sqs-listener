package com.jashmore.sqs.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.argument.UnsupportedArgumentResolutionException;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
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
    private static final CoreMessageProcessorTest BEAN = new CoreMessageProcessorTest();
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
            final Method method = getMethodWithAcknowledge();
            doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN);

            // act
            processor.processMessage(MESSAGE, NO_OP);

            // assert
            verify(argumentResolverService, times(2)).getArgumentResolver(any(MethodParameter.class));
        }

        @Test
        void anyParameterUnableToBeResolvedWillThrowAnError() {
            // arrange
            final Method method = getMethodWithAcknowledge();
            when(argumentResolverService.getArgumentResolver(any(MethodParameter.class)))
                    .thenThrow(new UnsupportedArgumentResolutionException());

            // act
            assertThrows(UnsupportedArgumentResolutionException.class,
                    () -> new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN));
        }

        @Test
        void methodWillBeInvokedWithArgumentsResolved() {
            // arrange
            final Method method = getMethodWithAcknowledge();
            final CoreMessageProcessorTest mockProcessor = mock(CoreMessageProcessorTest.class);
            doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
            when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(MESSAGE)))
                    .thenReturn("payload")
                    .thenReturn("payload2");
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, mockProcessor);

            // act
            processor.processMessage(MESSAGE, NO_OP);

            // assert
            verify(mockProcessor).methodWithAcknowledge(ArgumentMatchers.isA(Acknowledge.class), eq("payload"), eq("payload2"));
        }

        @Test
        void methodWithVisibilityExtenderWillBeCorrectlyResolved() {
            // arrange
            final Method method = getMethodWithVisibilityExtender();
            final CoreMessageProcessorTest mockProcessor = mock(CoreMessageProcessorTest.class);
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                    sqsAsyncClient, method, mockProcessor);

            // act
            processor.processMessage(MESSAGE, NO_OP);

            // assert
            verify(mockProcessor).methodWithVisibilityExtender(any(VisibilityExtender.class));
        }

        @Nested
        class AcknowledgeArgument {
            @Test
            void methodWithAcknowledgeParameterWillNotDeleteMessageOnSuccess() {
                // arrange
                final Method method = getMethodWithAcknowledge();
                doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
                when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(MESSAGE)))
                        .thenReturn("payload");
                final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, BEAN);

                // act
                processor.processMessage(MESSAGE, mockMessageResolver);

                // assert
                verify(mockMessageResolver, never()).get();
            }
        }

        @Test
        void anyArgumentThatFailsToBeResolvedForMessageWillThrowMessageProcessingException() {
            // arrange
            final Method method = getMethodWithAcknowledge();
            doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
            when(mockArgumentResolver.resolveArgumentForParameter(any(), any(), any()))
                    .thenThrow(new ExpectedTestException());
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN);

            // act
            final MessageProcessingException exception = assertThrows(MessageProcessingException.class, () -> processor.processMessage(MESSAGE, NO_OP));

            // assert
            assertThat(exception).hasCauseInstanceOf(ExpectedTestException.class);
        }
    }

    @Nested
    class SynchronousMessageProcessing {
        @Test
        void willResolveMessageOnExecutionWithoutException() {
            // arrange
            final Method method = getMethodWithNoArguments();
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN);
            when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(result).isDone();
        }

        @Test
        void methodThatThrowsExceptionDoesNotResolveMessage() {
            // arrange
            final Method method = getMethodThatThrowsException();
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN);

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // arrange
            assertThat(result).isCompletedExceptionally();
            verify(mockMessageResolver, never()).get();
        }

        @Test
        void failingToResolveMessageWillNotRejectFuture() {
            // arrange
            final Method method = getMethodWithNoArguments();
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN);
            when(mockMessageResolver.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(result).isCompleted();
            verify(mockMessageResolver).get();
        }

        @Test
        void failingToRunResolveMessageSupplierWillNotRejectFuture() {
            // arrange
            final Method method = getMethodWithNoArguments();
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN);
            when(mockMessageResolver.get()).thenThrow(ExpectedTestException.class);

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(result).isCompleted();
            verify(mockMessageResolver).get();
        }

        @Test
        void failingToProcessDueToIllegalAccessExceptionWillThrowMessageProcessingException() {
            // arrange
            final Method method = getPrivateMethod();
            final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN);

            // act
            final MessageProcessingException exception = assertThrows(MessageProcessingException.class, () -> processor.processMessage(MESSAGE, mockMessageResolver));

            // assert
            assertThat(exception).hasCauseInstanceOf(IllegalAccessException.class);
        }
    }

    @Nested
    class AsynchronousMessageProcessing {
        private final Method method = getMethodReturningCompletableFuture();

        private CompletableFuture<Object> completableFuture;

        @Mock
        private ArgumentResolver<CompletableFuture<Object>> completableFutureArgumentResolver;

        private MessageProcessor processor;

        @BeforeEach
        void setUp() {
            completableFuture = new CompletableFuture<>();
            doReturn(completableFutureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
            when(completableFutureArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(MethodParameter.class), eq(MESSAGE)))
                    .thenReturn(completableFuture);

            processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN);
        }

        @Test
        void willResolveMessageWhenFutureResolved() {
            // arrange
            when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

            // act
            CompletableFuture.runAsync(() -> processor.processMessage(MESSAGE, mockMessageResolver));

            // assert
            verify(mockMessageResolver, never()).get();
            completableFuture.complete("value");
            verify(mockMessageResolver, timeout(5000)).get();
        }

        @Test
        void willNotResolveMessageWhenFutureRejected() {
            // arrange
            completableFuture.completeExceptionally(new ExpectedTestException());

            // act
            final CompletableFuture<?> result = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(result).isCompletedExceptionally();
            verify(mockMessageResolver, never()).get();
        }

        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void thatReturnsNullWillMessageProcessingException() {
            // arrange
            when(completableFutureArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(MethodParameter.class), eq(MESSAGE)))
                    .thenReturn(null);

            // act
            final CompletableFuture<?> messageProcessingFuture = processor.processMessage(MESSAGE, NO_OP);

            // assert
            assertThat(messageProcessingFuture).isCompletedExceptionally();
        }

        @Test
        void thatResolvesButMessageResolvingFailsWillNotRejectFuture() {
            // arrange
            completableFuture.complete(null);
            when(mockMessageResolver.get()).thenThrow(ExpectedTestException.class);

            // act
            final CompletableFuture<?> messageProcessingFuture = processor.processMessage(MESSAGE, mockMessageResolver);

            // assert
            assertThat(messageProcessingFuture).isCompleted();
        }

        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void willThrowMessageProcessingExceptionIfMethodThrowsExceptionInsteadOfReturningCompletableFuture() {
            // arrange
            final Method method = getTestingMethod("methodReturningCompletableFutureThatThrowsException");
            processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, method, BEAN);

            // act
            final MessageProcessingException exception = assertThrows(MessageProcessingException.class, () -> processor.processMessage(MESSAGE, mockMessageResolver));

            // assert
            assertThat(exception).hasCauseInstanceOf(ExpectedTestException.class);
        }
    }

    @SuppressWarnings("unused")
    public void methodWithNoArguments() {

    }

    @SuppressWarnings("unused")
    public void methodWithNoAcknowledge(@Payload String payload, @Payload String payloadTwo) {

    }

    @SuppressWarnings( {"unused", "WeakerAccess"})
    public void methodWithAcknowledge(Acknowledge acknowledge, @Payload String payload, @Payload String payloadTwo) {

    }

    @SuppressWarnings( {"unused", "WeakerAccess"})
    public void methodWithVisibilityExtender(VisibilityExtender visibilityExtender) {

    }

    @SuppressWarnings( {"unused", "WeakerAccess"})
    public CompletableFuture<?> methodReturningCompletableFuture(CompletableFuture<?> futureToReturn) {
        return futureToReturn;
    }

    @SuppressWarnings( {"unused", "WeakerAccess"})
    public CompletableFuture<?> methodReturningCompletableFutureThatThrowsException() {
        throw new ExpectedTestException();
    }

    @SuppressWarnings("unused")
    public void methodThatThrowsException() {
        throw new RuntimeException("error");
    }

    @SuppressWarnings("unused")
    private void privateMethod() {

    }

    private Method getPrivateMethod() {
        return getTestingMethod("privateMethod");
    }

    private static Method getMethodWithNoArguments() {
        return getTestingMethod("methodWithNoArguments");
    }

    private static Method getMethodWithAcknowledge() {
        return getTestingMethod("methodWithAcknowledge", Acknowledge.class, String.class, String.class);
    }

    private static Method getMethodThatThrowsException() {
        return getTestingMethod("methodThatThrowsException");
    }

    private static Method getMethodWithVisibilityExtender() {
        return getTestingMethod("methodWithVisibilityExtender", VisibilityExtender.class);
    }

    private static Method getMethodReturningCompletableFuture() {
        return getTestingMethod("methodReturningCompletableFuture", CompletableFuture.class);
    }

    private static Method getTestingMethod(final String methodName, final Class<?>... parameterClasses) {
        try {
            return CoreMessageProcessorTest.class.getDeclaredMethod(methodName, parameterClasses);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
    }
}
