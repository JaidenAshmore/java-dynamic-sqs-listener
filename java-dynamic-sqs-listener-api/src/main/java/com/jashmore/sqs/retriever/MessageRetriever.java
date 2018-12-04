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
     * Retrieve a message from the queue now if there are any available.
     *
     * <p>This will not keep waiting until a message is placed onto the queue.
     *
     * @return the optional message obtained from the queue
     * @throws InterruptedException if the thread was interrupted while waiting for a message
     */
    Optional<Message> retrieveMessageNow() throws InterruptedException;

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

    /**
     * Retrieve a single message from the queue within the given time period.
     *
     * <p>This is a blocking operation so it will wait until a message can be taken from the queue within the given period. Note that this operation may
     * not perfectly align with the requested timeout due to implementation or other processing concerns, therefore the timeout should just be considered
     * as a suggestion.  However, implementers of this method should always strive to being as close to the timeout as possible.
     *
     * <p>The timeout amount must always be greater than or equal to zero. If a negative number is submitted a {@link IllegalArgumentException} will be thrown.
     *
     * @param timeout  the number of time units to wait, e.g. 5 of "5 seconds"
     * @param timeUnit the unit of time for the timeout, e.g. seconds of "5 seconds"
     * @return the message to process if there is any or {@link Optional#empty()} if none was obtained in the time period
     * @throws IllegalArgumentException if the arguments are in an incorrect format
     * @throws InterruptedException if the thread was interrupted while waiting for a message
     */
    Optional<Message> retrieveMessage(@Min(0) long timeout, @NotNull TimeUnit timeUnit) throws InterruptedException;
}
