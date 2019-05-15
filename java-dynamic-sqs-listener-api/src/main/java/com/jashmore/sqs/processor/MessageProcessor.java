package com.jashmore.sqs.processor;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.resolver.MessageResolver;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Processor that has the responsibility of taking a message and processing it via the required message consumer (Java method).
 *
 * <p>This would therefore need to know what message consumer (method) should be executed for this message and how to execute it. The default
 * behaviour of the processor should be if the method processing the method completes without an exception, the message should be resolved by
 * a {@link MessageResolver}.  However, if the method throws an exception the message processing will be considered a failure and will not be resolved.
 *
 * <p>Instead of the approach above of using an exception to consider failures you can have the Java method return a {@link CompletableFuture} and
 * if that is resolved the message should be considered completed successfully and should be resolved. However, if the {@link CompletableFuture}
 * is rejected the message should not be resolved, just the same as if an exception was thrown.
 *
 * <p>However, if an {@link Acknowledge} parameter is included in the method signature, neither scenarios above should resolve the message and only
 * calls to {@link Acknowledge#acknowledgeSuccessful()} should resolve the message.
 *
 * <p>The parameters of the function will need to extract data out of the message and the implementations of this {@link MessageProcessor} should use
 * the {@link ArgumentResolverService}. For example, an argument may require a parameter to contain the message id of the message
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
