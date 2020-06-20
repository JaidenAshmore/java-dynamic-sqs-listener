package com.jashmore.sqs.processor;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import com.jashmore.sqs.resolver.MessageResolver;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Processor that has the responsibility of taking a message and processing it via the required message consumer (Java method).
 *
 * <p>This wraps a Java method that should be executed for each message received. The parameters of the function will need to be extracted out
 * of the message and the implementations of this {@link MessageProcessor} should use the {@link ArgumentResolverService} to do this. For example, an argument
 * may require the message id of the message and therefore this would handle populating the argument for this parameter with this value.
 *
 * <p>Most arguments would be able to be resolved via the {@link ArgumentResolverService}, however, the following arguments must be resolved via
 * this {@link MessageProcessor}:
 *
 * <ul>
 *     <li>{@link Acknowledge}: this argument can be used to acknowledge that the messages has been successfully processed and can be deleted from the
 *     queue.  If no {@link Acknowledge} argument is included in the argument list of the method, the message will be resolved based on the
 *     execution and return type of the method.</li>
 *     <li>{@link VisibilityExtender}: this argument can be used to extend the visibility of a message if it is taking a long time to process.</li>
 * </ul>
 *
 * <p>A message can be considered a success and therefore deleted from the queue, see {@link MessageResolver}, if one of these scenarios occurs:
 * <ul>
 *     <li>the method has an {@link Acknowledge} field as a parameter, the {@link Acknowledge#acknowledgeSuccessful()} is called in the method and
 *     that is completed successfully</li>
 *     <li>or the method returns a {@link CompletableFuture} which is resolved, the process will call the resolve message callback after this</li>
 *     <li>or the method does not return a {@link CompletableFuture} and executes without throwing an exception, in which the resolve
 *     message callback is called</li>
 * </ul>
 *
 * <p>If you were to consider this library as similar to a pub-sub system, this could be considered the function that executes the underlying subscriber
 * in that it will take messages provided by the {@link MessageBroker}.
 *
 * <p>As there could be multiple messages all being processed at once, the implementations of this interface must be thread safe.
 */
@ThreadSafe
public interface MessageProcessor {
    /**
     * Process the message received on the queue.
     *
     * <p>This method returns a {@link CompletableFuture} that is either resolved or rejected based on whether the message processing was able to be
     * successfully completed. If the message processing is successful but the resolve message callback subsequently failed, the future will still be
     * successful.
     *
     * <p>The completion of this future does not guarantee that the resolve callback was actually called because the message listener
     * may be processing it asynchronously with the {@link Acknowledge} parameter.
     *
     * @param message                the message to process
     * @param resolveMessageCallback the callback that should be run when the message was processed successfully
     * @return future that is resolved when the message was processed and another thread can be picked up, if there was an error processing the message
     *      this will be rejected with a {@link MessageProcessingException}
     * @throws MessageProcessingException if the message could not be supplied to the message listener or the message listener failed without returning
     *      the {@link CompletableFuture} when processing asynchronously
     */
    CompletableFuture<?> processMessage(Message message, Supplier<CompletableFuture<?>> resolveMessageCallback);
}
