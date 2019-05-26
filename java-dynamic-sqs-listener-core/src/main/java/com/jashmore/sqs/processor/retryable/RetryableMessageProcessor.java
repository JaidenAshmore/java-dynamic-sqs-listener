package com.jashmore.sqs.processor.retryable;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.processor.MessageProcessingException;
import com.jashmore.sqs.processor.MessageProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Message processor that will attempt multiple times to process a message if it fails the first time.
 *
 * <p>This could be a useful implementation if there isn't a re-drive policy set up for the queue and therefore you want to provide retry attempts without
 * modifying the queue.
 */
@Slf4j
@AllArgsConstructor
public class RetryableMessageProcessor implements MessageProcessor {
    private final MessageProcessor delegateMessageProcessor;
    private final RetryableMessageProcessorProperties properties;

    @Override
    public void processMessage(final Message message) throws MessageProcessingException {
        processMessageWithRetries(message, properties.getRetryAttempts() - 1);
    }

    /**
     * Process a message and if it fails try a number of times again for it to be successfully processed.
     *
     * @param message       the message to process
     * @param retryAttempts the number of times to retry to process a message
     * @throws MessageProcessingException exception thrown if the message was not able to be processed within the given retry amount
     */
    private void processMessageWithRetries(final Message message, final int retryAttempts) throws MessageProcessingException {
        try {
            delegateMessageProcessor.processMessage(message);
        } catch (final RuntimeException runtimeException) {
            if (retryAttempts <= 0) {
                throw runtimeException;
            }

            log.error("Error processing message. Trying " + retryAttempts + " more times", runtimeException);
            try {
                backoff();
            } catch (final InterruptedException exception) {
                throw new MessageProcessingException("Interrupted while processing message", exception);
            }
            processMessageWithRetries(message, retryAttempts - 1);
        }
    }

    /**
     * Sleep the thread for a certain period of time so that this thread is not constantly spinning and throwing errors.
     *
     * <p>Visible for testing so the backoff/sleep can be used to test concurrency.
     *
     * @throws InterruptedException if the thread was interrupted while sleeping
     */
    @VisibleForTesting
    void backoff() throws InterruptedException {
        Thread.sleep(properties.getRetryDelayInMs());
    }
}
