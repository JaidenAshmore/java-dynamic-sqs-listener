package com.jashmore.sqs.retriever;

import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.ThreadSafe;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Class used for retrieving messages to execute from the queue.
 *
 * <p>If you were to consider this library as similar to a pub-sub system, this could be considered the publisher.  It polls for messages from the
 * remote queue which will be taken by the {@link MessageBroker} and transferred to the corresponding {@link MessageProcessor} that knows how to process
 * this message.
 *
 * <p>As there could be multiple threads wanting to process messages the implementations of this class must be thread safe.
 */
@ThreadSafe
public interface MessageRetriever {
    /**
     * Retrieve a single message from the queue and if there are no messages currently in the queue it will keep polling until a message eventually is
     * retrieved.
     *
     * <p>This is a blocking operation and will wait indefinitely until a message can be taken from the queue or the thread is interrupted.
     *
     * @return the message obtained from the queue
     * @throws InterruptedException if the thread was interrupted while waiting for a message
     */
    Message retrieveMessage() throws InterruptedException;
}
