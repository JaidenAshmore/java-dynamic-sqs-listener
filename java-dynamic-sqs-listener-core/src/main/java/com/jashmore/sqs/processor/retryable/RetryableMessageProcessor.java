package com.jashmore.sqs.processor.retryable;

import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.processor.MessageProcessingException;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.util.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
        } catch (final Throwable throwable) {
            if (retryAttempts > 0) {
                log.error("Error processing message. Trying " + retryAttempts + " more times", throwable);
                try {
                    backoff();
                } catch (final InterruptedException exception) {
                    throw new MessageProcessingException("Interrupted while processing message", exception);
                }
                processMessageWithRetries(message, retryAttempts - 1);
            } else {
                throw throwable;
            }
        }
    }

    @VisibleForTesting
    void backoff() throws InterruptedException {
        Thread.sleep(properties.getRetryDelayInMs());
    }
}
