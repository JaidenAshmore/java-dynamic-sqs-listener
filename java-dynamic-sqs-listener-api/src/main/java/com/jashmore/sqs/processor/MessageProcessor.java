package com.jashmore.sqs.processor;

import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.MessageBroker;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Processor that has the responsibility of handling taking a message and getting it processed by the required message consumer (Java method).
 *
 * <p>This would therefore need to know what message consumer (method) should be executed for this message and how to execute it.
 * During the message processing it may need to extract data out of the message to populate the parameters of the method with arguments
 * fulfilling what is required. For example, an argument may require a parameter to contain the message id of the message
 * and therefore this would handle populating the argument for this parameter with this value. See
 * {@link ArgumentResolverService} for how arguments can be resolved from a message.
 *
 * <p>This also has the responsibility of determining if the message was successful and if it is what it should do with the
 * message. The most common scenario is to delete the message from the queue when it has been completed without an exception
 * because it is not needing to be processed another time.
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
