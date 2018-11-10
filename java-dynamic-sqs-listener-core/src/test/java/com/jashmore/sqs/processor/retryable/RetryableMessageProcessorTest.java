package com.jashmore.sqs.processor.retryable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.processor.MessageProcessingException;
import com.jashmore.sqs.processor.MessageProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RetryableMessageProcessorTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MessageProcessor delegateMessageProcessor;

    @Test
    public void whenThereAreFailuresTheMessageWillBeRetriedTheMaximumNumberOfTimes() {
        // arrange
        final int retryAmount = 3;
        final RetryableMessageProcessorProperties properties = RetryableMessageProcessorProperties
                .builder()
                .retryDelayInMs(0)
                .retryAttempts(retryAmount)
                .build();
        final MessageProcessor retryableMessageProcessor = new RetryableMessageProcessor(delegateMessageProcessor, properties);
        doThrow(new MessageProcessingException("error")).when(delegateMessageProcessor).processMessage(any(Message.class));

        try {
            // act
            retryableMessageProcessor.processMessage(new Message());
            fail("Expected an exception to be thrown");
        } catch (final MessageProcessingException messageProcessingException) {
            // assert
            verify(delegateMessageProcessor, times(retryAmount)).processMessage(any(Message.class));
        }
    }

    @Test
    public void doesNotAttemptMessageProcessingAnyMoreTimesWhenSuccessful() {
        // arrange
        final int retryAmount = 3;
        final RetryableMessageProcessorProperties properties = RetryableMessageProcessorProperties
                .builder()
                .retryDelayInMs(0)
                .retryAttempts(retryAmount)
                .build();
        final MessageProcessor retryableMessageProcessor = new RetryableMessageProcessor(delegateMessageProcessor, properties);

        // act
        retryableMessageProcessor.processMessage(new Message());

        // assert
        verify(delegateMessageProcessor).processMessage(any(Message.class));
    }

    @Test
    public void shouldBackoffWhenThereWasFailuresToProcessTheMessage() {
        // arrange
        final long currentTime = System.currentTimeMillis();
        final int retryAmount = 3;
        final int retryDelayInMs = 100;
        final RetryableMessageProcessorProperties properties = RetryableMessageProcessorProperties
                .builder()
                .retryDelayInMs(100)
                .retryAttempts(retryAmount)
                .build();
        final MessageProcessor retryableMessageProcessor = new RetryableMessageProcessor(delegateMessageProcessor, properties);
        doThrow(new MessageProcessingException("error")).when(delegateMessageProcessor).processMessage(any(Message.class));

        try {
            // act
            retryableMessageProcessor.processMessage(new Message());
            fail("Expected an exception to be thrown");
        } catch (final MessageProcessingException messageProcessingException) {
            // assert
            final long timeFinished = System.currentTimeMillis();
            assertThat(timeFinished - currentTime).isGreaterThanOrEqualTo((retryAmount - 1L) * retryDelayInMs);
            verify(delegateMessageProcessor, times(retryAmount)).processMessage(any(Message.class));
        }
    }

    @Test
    public void interruptedWhileBackingOffOnRetryThrowsException() {
        // arrange
        final RetryableMessageProcessorProperties properties = RetryableMessageProcessorProperties
                .builder()
                .retryDelayInMs(100)
                .retryAttempts(2)
                .build();
        final MessageProcessor retryableMessageProcessor = new RetryableMessageProcessor(delegateMessageProcessor, properties) {
            @Override
            void backoff() throws InterruptedException {
                throw new InterruptedException();
            }
        };
        doThrow(new MessageProcessingException("error")).when(delegateMessageProcessor).processMessage(any(Message.class));

        // act
        try {
            retryableMessageProcessor.processMessage(new Message());
            fail("Should have failed processing message");
        } catch (final MessageProcessingException exception) {
            // assert
            assertThat(exception).hasCause(new InterruptedException());
        }
    }
}
