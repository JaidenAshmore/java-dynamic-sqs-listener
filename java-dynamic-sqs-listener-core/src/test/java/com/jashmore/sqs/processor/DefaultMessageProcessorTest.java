package com.jashmore.sqs.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.resolver.MessageResolver;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class DefaultMessageProcessorTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final DefaultMessageProcessorTest BEAN = new DefaultMessageProcessorTest();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private MessageResolver messageResolver;

    @Test
    public void forEachParameterInMethodTheArgumentIsResolved() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(QUEUE_URL)
                .build();
        final Message message = Message.builder().build();
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, queueProperties, messageResolver, method, BEAN);
        when(argumentResolverService.resolveArgument(eq(queueProperties), any(Parameter.class), eq(message)))
                .thenReturn("payload")
                .thenReturn("payload2");

        // act
        processor.processMessage(message);

        // assert
        verify(argumentResolverService, times(2)).resolveArgument(eq(queueProperties), any(Parameter.class), eq(message));
    }

    @Test
    public void anyParameterUnableToBeResolvedWillThrowAnError() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(QUEUE_URL)
                .build();
        final Message message = Message.builder().build();
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, queueProperties, messageResolver, method, BEAN);
        when(argumentResolverService.resolveArgument(eq(queueProperties), any(Parameter.class), eq(message)))
                .thenThrow(new ArgumentResolutionException("Error resolving"));

        // act
        try {
            processor.processMessage(message);
            fail("Should have thrown an exception");
        } catch (final MessageProcessingException exception) {
            // assert
            assertThat(exception).hasCauseInstanceOf(ArgumentResolutionException.class);
        }
    }

    @Test
    public void methodWillBeInvokedWithArgumentsResolved() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        final DefaultMessageProcessorTest mockProcessor = mock(DefaultMessageProcessorTest.class);
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(QUEUE_URL)
                .build();
        final Message message = Message.builder().build();
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, queueProperties, messageResolver, method, mockProcessor);
        when(argumentResolverService.resolveArgument(eq(queueProperties), any(Parameter.class), eq(message)))
                .thenReturn("payload")
                .thenReturn("payload2");

        // act
        processor.processMessage(message);

        // assert
        verify(mockProcessor).methodWithAcknowledge(ArgumentMatchers.isA(Acknowledge.class), eq("payload"), eq("payload2"));
    }

    @Test
    public void methodWithAcknowledgeParameterWillNotDeleteMessageOnSuccess() {
        // arrange
        final Method method = getMethodWithAcknowledge();
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(QUEUE_URL)
                .build();
        final Message message = Message.builder().build();
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, queueProperties, messageResolver, method, BEAN);
        when(argumentResolverService.resolveArgument(eq(queueProperties), any(Parameter.class), eq(message)))
                .thenReturn("payload");

        // act
        processor.processMessage(message);

        // assert
        verify(messageResolver, never()).resolveMessage(message);
    }

    @Test
    public void methodWithoutAcknowledgeParameterWillDeleteMessageOnSuccess() {
        // arrange
        final Method method = getMethodWithNoAcknowledge();
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(QUEUE_URL)
                .build();
        final Message message = Message.builder().receiptHandle("handle").build();
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, queueProperties, messageResolver, method, BEAN);
        when(argumentResolverService.resolveArgument(eq(queueProperties), any(Parameter.class), eq(message)))
                .thenReturn("payload");

        // act
        processor.processMessage(message);

        // assert
        verify(messageResolver).resolveMessage(message);
    }


    @Test
    public void methodWithoutAcknowledgeThatThrowsExceptionDoesNotDeleteMessage() {
        // arrange
        final Method method = getMethodThatThrowsException();
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(QUEUE_URL)
                .build();
        final Message message = Message.builder().receiptHandle("handle").build();
        final MessageProcessor processor = new DefaultMessageProcessor(argumentResolverService, queueProperties, messageResolver, method, BEAN);
        when(argumentResolverService.resolveArgument(eq(queueProperties), any(Parameter.class), eq(message)))
                .thenReturn("payload");

        // act
        try {
            processor.processMessage(message);
            fail("Should have thrown exception");
        } catch (final MessageProcessingException exception) {
            // assert
            verify(messageResolver, never()).resolveMessage(message);
        }
    }

    @SuppressWarnings("unused")
    public void methodWithNoAcknowledge(@Payload String payload, @Payload String payloadTwo) {

    }

    @SuppressWarnings( {"unused", "WeakerAccess"})
    public void methodWithAcknowledge(Acknowledge acknowledge, @Payload String payload, @Payload String payloadTwo) {

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

    private static Method getTestingMethod(final String methodName, final Class<?>... parameterClasses) {
        try {
            return DefaultMessageProcessorTest.class.getMethod(methodName, parameterClasses);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
    }
}
