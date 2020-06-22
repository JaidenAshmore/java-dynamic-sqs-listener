package com.jashmore.sqs.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.argument.UnsupportedArgumentResolutionException;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@ExtendWith(MockitoExtension.class)
class CoreMessageProcessorTest {
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties
            .builder()
            .queueUrl("queueUrl")
            .build();
    private static final CoreMessageProcessorTest BEAN = new CoreMessageProcessorTest();
    private static final Runnable NO_OP = () -> {
    };

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private ArgumentResolver<String> mockArgumentResolver;

    @Mock
    private ArgumentResolver<CompletableFuture<Object>> completableFutureArgumentResolver;

    @Test
    void forEachParameterInMethodTheArgumentIsResolved() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        final Message message = Message.builder().build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, BEAN);

        // act
        processor.processMessage(message, NO_OP);

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
        final Message message = Message.builder().build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(message)))
                .thenReturn("payload")
                .thenReturn("payload2");
        final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, mockProcessor);

        // act
        processor.processMessage(message, NO_OP);

        // assert
        verify(mockProcessor).methodWithAcknowledge(ArgumentMatchers.isA(Acknowledge.class), eq("payload"), eq("payload2"));
    }

    @Test
    void methodWithVisibilityExtenderWillBeCorrectlyResolved() {
        // arrange
        final Method method = getMethodWithVisibiltyExtender();
        final CoreMessageProcessorTest mockProcessor = mock(CoreMessageProcessorTest.class);
        final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, mockProcessor);
        final Message message = Message.builder().build();

        // act
        processor.processMessage(message, NO_OP);

        // assert
        verify(mockProcessor).methodWithVisibilityExtender(any(VisibilityExtender.class));
    }

    @Test
    void methodWithAcknowledgeParameterWillNotDeleteMessageOnSuccess() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        final Message message = Message.builder().build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(message)))
                .thenReturn("payload");
        final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, BEAN);
        final Runnable messageResolvedRunnable = mock(Runnable.class);

        // act
        processor.processMessage(message, messageResolvedRunnable);

        // assert
        verify(messageResolvedRunnable, never()).run();
    }

    @Test
    void methodWithoutAcknowledgeParameterWillResolveMessageOnExecutionWithoutException() {
        // arrange
        final Method method = getMethodWithNoAcknowledge();
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(message)))
                .thenReturn("payload");
        final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, BEAN);
        final Runnable messageResolvedRunnable = mock(Runnable.class);

        // act
        processor.processMessage(message, messageResolvedRunnable);

        // assert
        verify(messageResolvedRunnable).run();
    }

    @Test
    void methodWithoutAcknowledgeThatThrowsExceptionDoesNotResolveMessage() {
        // arrange
        final Method method = getMethodThatThrowsException();
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(message)))
                .thenReturn("payload");
        final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, BEAN);
        final Runnable messageResolvedRunnable = mock(Runnable.class);

        // act
        final CompletableFuture<?> result = processor.processMessage(message, messageResolvedRunnable);

        // arrange
        assertThat(result).isCompletedExceptionally();
        verify(messageResolvedRunnable, never()).run();
    }

    @Test
    void methodReturningCompletableFutureWillResolveMessageWhenFutureResolved() throws Exception {
        // arrange
        final Method method = CoreMessageProcessorTest.class.getMethod("methodReturningCompletableFuture", CompletableFuture.class);
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(completableFutureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
        final CompletableFuture<Object> future = new CompletableFuture<>();
        when(completableFutureArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(MethodParameter.class), eq(message)))
                .thenReturn(future);
        final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, BEAN);
        final Runnable messageResolvedRunnable = mock(Runnable.class);

        // act
        CompletableFuture.runAsync(() -> processor.processMessage(message, messageResolvedRunnable));

        // assert
        verify(messageResolvedRunnable, never()).run();
        future.complete("value");
        verify(messageResolvedRunnable, timeout(5000)).run();
    }

    @Test
    void methodReturningCompletableFutureWillNotResolveMessageWhenFutureRejected() throws Exception {
        // arrange
        final Method method = CoreMessageProcessorTest.class.getMethod("methodReturningCompletableFuture", CompletableFuture.class);
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(completableFutureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
        final CompletableFuture<Object> future = new CompletableFuture<>();
        when(completableFutureArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(MethodParameter.class), eq(message)))
                .thenReturn(future);
        final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, BEAN);
        final Runnable messageResolvedRunnable = mock(Runnable.class);
        future.completeExceptionally(new RuntimeException("Excepted Exception"));

        // act
        final CompletableFuture<?> result = processor.processMessage(message, messageResolvedRunnable);

        // assert
        assertThat(result).isCompletedExceptionally();
        verify(messageResolvedRunnable, never()).run();
    }

    @Test
    void methodReturningCompletableFutureThatReturnsNullWillThrowMessageProcessingException() throws Exception {
        // arrange
        final Method method = CoreMessageProcessorTest.class.getMethod("methodReturningCompletableFuture", CompletableFuture.class);
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(completableFutureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
        when(completableFutureArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(MethodParameter.class), eq(message)))
                .thenReturn(null);
        final MessageProcessor processor = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, BEAN);

        // act
        final ExecutionException exception = assertThrows(ExecutionException.class, () -> processor.processMessage(message, NO_OP).get());

        // assert
        assertThat(exception.getCause()).isInstanceOf(MessageProcessingException.class);
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

    @SuppressWarnings("WeakerAccess")
    public CompletableFuture<?> methodReturningCompletableFuture(CompletableFuture<?> futureToReturn) {
        return futureToReturn;
    }

    @SuppressWarnings("unused")
    public void methodThatThrowsException(@Payload String payload) {
        throw new RuntimeException("error");
    }

    private static Method getMethodWithNoAcknowledge() {
        return getTestingMethod("methodWithNoAcknowledge", String.class, String.class);
    }

    private static Method getMethodWithAcknowledge() {
        return getTestingMethod("methodWithAcknowledge", Acknowledge.class, String.class, String.class);
    }

    private static Method getMethodThatThrowsException() {
        return getTestingMethod("methodThatThrowsException", String.class);
    }

    private static Method getMethodWithVisibiltyExtender() {
        return getTestingMethod("methodWithVisibilityExtender", VisibilityExtender.class);
    }

    private static Method getTestingMethod(final String methodName, final Class<?>... parameterClasses) {
        try {
            return CoreMessageProcessorTest.class.getMethod(methodName, parameterClasses);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
    }
}
