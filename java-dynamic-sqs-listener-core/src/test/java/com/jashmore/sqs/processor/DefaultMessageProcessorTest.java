package com.jashmore.sqs.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
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
import com.jashmore.sqs.resolver.MessageResolver;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings( {"WeakerAccess", "unused"})
public class DefaultMessageProcessorTest {
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties
            .builder()
            .queueUrl("queueUrl")
            .build();
    private static final DefaultMessageProcessorTest BEAN = new DefaultMessageProcessorTest();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private MessageResolver messageResolver;

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private ArgumentResolver<String> mockArgumentResolver;

    @Mock
    private ArgumentResolver<CompletableFuture<Object>> completableFutureArgumentResolver;

    @Test
    public void forEachParameterInMethodTheArgumentIsResolved() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        final Message message = Message.builder().build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, messageResolver, method, BEAN);

        // act
        processor.processMessage(message);

        // assert
        verify(argumentResolverService, times(2)).getArgumentResolver(any(MethodParameter.class));
    }

    @Test
    public void anyParameterUnableToBeResolvedWillThrowAnError() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        when(argumentResolverService.getArgumentResolver(any(MethodParameter.class)))
                .thenThrow(new UnsupportedArgumentResolutionException());
        expectedException.expect(UnsupportedArgumentResolutionException.class);

        // act
        new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES, sqsAsyncClient, messageResolver, method, BEAN);
    }

    @Test
    public void methodWillBeInvokedWithArgumentsResolved() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        final DefaultMessageProcessorTest mockProcessor = mock(DefaultMessageProcessorTest.class);
        final Message message = Message.builder().build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(message)))
                .thenReturn("payload")
                .thenReturn("payload2");
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, messageResolver, method, mockProcessor);

        // act
        processor.processMessage(message);

        // assert
        verify(mockProcessor).methodWithAcknowledge(ArgumentMatchers.isA(Acknowledge.class), eq("payload"), eq("payload2"));
    }

    @Test
    public void methodWithAcknowledgeParameterWillNotDeleteMessageOnSuccess() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        final Message message = Message.builder().build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(message)))
                .thenReturn("payload");
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, messageResolver, method, BEAN);

        // act
        processor.processMessage(message);

        // assert
        verify(messageResolver, never()).resolveMessage(message);
    }

    @Test
    public void methodWithoutAcknowledgeParameterWillDeleteMessageOnSuccess() {
        // arrange
        final Method method = getMethodWithNoAcknowledge();
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(message)))
                .thenReturn("payload");
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, messageResolver, method, BEAN);

        // act
        processor.processMessage(message);

        // assert
        verify(messageResolver).resolveMessage(message);
    }


    @Test
    public void methodWithoutAcknowledgeThatThrowsExceptionDoesNotDeleteMessage() {
        // arrange
        final Method method = getMethodThatThrowsException();
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(mockArgumentResolver).when(argumentResolverService).getArgumentResolver(any(MethodParameter.class));
        when(mockArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(), eq(message)))
                .thenReturn("payload");
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, messageResolver, method, BEAN);

        // act
        try {
            processor.processMessage(message);
            fail("Should have thrown exception");
        } catch (final MessageProcessingException exception) {
            // assert
            verify(messageResolver, never()).resolveMessage(message);
        }
    }

    @Test
    public void methodReturningCompletableFutureWillResolveMessageWhenFutureResolved() throws Exception {
        // arrange
        final Method method = DefaultMessageProcessorTest.class.getMethod("methodReturningCompletableFuture", CompletableFuture.class);
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(completableFutureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
        final CompletableFuture<Object> future = new CompletableFuture<>();
        when(completableFutureArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(MethodParameter.class), eq(message)))
                .thenReturn(future);
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, messageResolver, method, BEAN);

        // act
        CompletableFuture.runAsync(() -> processor.processMessage(message));

        // assert
        verify(messageResolver, never()).resolveMessage(message);
        future.complete("value");
        verify(messageResolver, timeout(100)).resolveMessage(message);
    }

    @Test
    public void methodReturningCompletableFutureWillNotResolveMessageWhenFutureRejected() throws Exception {
        // arrange
        final Method method = DefaultMessageProcessorTest.class.getMethod("methodReturningCompletableFuture", CompletableFuture.class);
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(completableFutureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
        final CompletableFuture<Object> future = new CompletableFuture<>();
        when(completableFutureArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(MethodParameter.class), eq(message)))
                .thenReturn(future);
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, messageResolver, method, BEAN);

        // act
        CompletableFuture.runAsync(() -> processor.processMessage(message));
        future.completeExceptionally(new RuntimeException("Excepted Exception"));
        Thread.sleep(100); // make sure it doesn't resolve

        // assert
        verify(messageResolver, never()).resolveMessage(message);
    }

    @Test
    public void methodReturningCompletableFutureThatReturnsNullWillThrowMessageProcessingException() throws Exception {
        // arrange
        final Method method = DefaultMessageProcessorTest.class.getMethod("methodReturningCompletableFuture", CompletableFuture.class);
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(completableFutureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
        when(completableFutureArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(MethodParameter.class), eq(message)))
                .thenReturn(null);
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, messageResolver, method, BEAN);
        expectedException.expect(MessageProcessingException.class);

        // act
        processor.processMessage(message);
    }

    @Test
    public void threadInterruptedWhileGettingMessageWillThrowException() throws Exception {
        // arrange
        final Method method = DefaultMessageProcessorTest.class.getMethod("methodReturningCompletableFuture", CompletableFuture.class);
        final Message message = Message.builder().receiptHandle("handle").build();
        doReturn(completableFutureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
        final CompletableFuture<Object> future = new CompletableFuture<>();
        when(completableFutureArgumentResolver.resolveArgumentForParameter(eq(QUEUE_PROPERTIES), any(MethodParameter.class), eq(message)))
                .thenReturn(future);
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, messageResolver, method, BEAN);

        // act
        final AtomicBoolean exceptionThrown = new AtomicBoolean(false);
        CompletableFuture.runAsync(() -> {
            try {
                Thread.currentThread().interrupt(); // interrupt the thread so the InterruptedException will be thrown while blocking on the future
                processor.processMessage(message);
            } catch (final MessageProcessingException messageProcessingException) {
                exceptionThrown.set(true);
            }
        }).get(1, TimeUnit.SECONDS);

        // assert
        assertThat(exceptionThrown).isTrue();
    }

    public void methodWithNoAcknowledge(@Payload String payload, @Payload String payloadTwo) {

    }

    public void methodWithAcknowledge(Acknowledge acknowledge, @Payload String payload, @Payload String payloadTwo) {

    }

    public CompletableFuture<?> methodReturningCompletableFuture(CompletableFuture<?> futureToReturn) {
        return futureToReturn;
    }

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

    private static Method getTestingMethod(final String methodName, final Class<?>... parameterClasses) {
        try {
            return DefaultMessageProcessorTest.class.getMethod(methodName, parameterClasses);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
    }
}
