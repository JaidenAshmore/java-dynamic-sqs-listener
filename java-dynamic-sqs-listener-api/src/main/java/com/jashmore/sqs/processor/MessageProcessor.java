package com.jashmore.sqs.processor;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.argument.Acknowledge;
import software.amazon.awssdk.services.sqs.model.Message;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Processor that has the responsibility of taking a message and processing it via the required message consumer (Java method).
 *
 * <p>This would therefore need to know what message consumer (method) should be executed for this message and how to execute it.
 * During the message processing it may need to extract data out of the message to populate the parameters of the method with arguments
 * fulfilling what is required. For example, an argument may require a parameter to contain the message id of the message
 * and therefore this would handle populating the argument for this parameter with this value.
 *
 * <p>Most arguments would be able to be resolved via the {@link ArgumentResolverService}, however, the following arguments must be resolved via
 * this {@link MessageProcessor}:
 *
 * <ul>
 *     <li>{@link Acknowledge}: this argument can be used to acknowledge that the messages has been successfully processed and can be deleted from the
 *     queue.  If no {@link Acknowledge} argument is included in the argument list of the method, the message will be deleted from the queue if the
 *     method processing the message completes without an exception being thrown.</li>
 * </ul>
 *
 * <p>If you were to consider this library as similar to a pub-sub system, this could be considered the subscriber in that it will take messages provided
 * by the {@link MessageBroker}.
 *
 * <p>As there could be multiple messages all being processed at once, the implementations of this interface must be thread safe.
 */
@ThreadSafe
public interface MessageProcessor {
    /**
     * Process the message received on the queue.
     *
     * @param message the message to process
     * @throws MessageProcessingException if there was an error processing the message, e.g. an exception was thrown by the delegate method
     */
    void processMessage(Message message) throws MessageProcessingException;
}
