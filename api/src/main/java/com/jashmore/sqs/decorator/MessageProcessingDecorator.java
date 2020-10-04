package com.jashmore.sqs.decorator;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.processor.argument.Acknowledge;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Used to decorate the thread that will be used to process the given message.
 *
 * <p>This can be used to add extra information to the thread, such as Tracing, metrics, logging, etc.
 */
@SuppressWarnings("unused")
@ThreadSafe
public interface MessageProcessingDecorator {
    /**
     * Apply decorations to the thread before the message is handed to the message listener to process.
     *
     * <p>If any of these decorators fail to perform this method, subsequent calls will not be made and the message will not be
     * processed by the underlying {@link MessageProcessor}.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     */
    default void onPreMessageProcessing(MessageProcessingContext context, Message message) {}

    /**
     * Method called when the processing of the message has failed.
     *
     * <p>If the message listener is <b>synchronous</b> (it does not return a {@link CompletableFuture}), this method will be invoked if the message listener
     * throws an exception. Execution of this method will be guaranteed to run on the same thread that the message listener was running in.
     *
     * <p>If the method is <b>asynchronous</b> (it returns a {@link CompletableFuture}), this method will be invoked if the message listener throws an exception
     * or the return {@link CompletableFuture} is rejected. If an exception is thrown, this method will guaranteed to be run on the same thread that
     * the message listener was running in. If the {@link CompletableFuture} is rejected, it is not guaranteed to be running on the same thread that
     * was processing the message.
     *
     * <p>If any of these decorators fail to perform this method, subsequent calls to the other decorators will still be performed.
     *
     * @param context   details about the message processing functionality, e.g. identifier for this message processor
     * @param message   the message being processed
     * @param throwable the exception thrown
     */
    default void onMessageProcessingFailure(MessageProcessingContext context, Message message, Throwable throwable) {}

    /**
     * Method called when the message has successfully been processed, but not necessarily that the message is considered a success and will be marked
     * as resolved.
     *
     * <p>If the message listener is <b>synchronous</b> (it does not return a {@link CompletableFuture}), this method will be invoked if the message listener
     * function is completed without throwing an exception. Execution of this method will be guaranteed to run on the same thread that the message listener was
     * running in.
     *
     * <p>If the method is <b>asynchronous</b> (it returns a {@link CompletableFuture}), this method will be invoked if the message listener function returns a
     * {@link CompletableFuture} and that is subsequently completed. This is not guaranteed to be running on the same thread that was processing the message.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     * @param object  the value that was resolved from the message listener function or {@link CompletableFuture}
     */
    default void onMessageProcessingSuccess(MessageProcessingContext context, Message message, @Nullable Object object) {}

    /**
     * Method called when the thread that was used to execute the message listener is done.
     *
     * <p>This can be used to tidy up any thread locals  that may have been set up in the {@link #onPreMessageProcessing(MessageProcessingContext, Message)}
     * or within the message listener.
     *
     * <p>If the message listener is <b>synchronous</b> (it does not return a {@link CompletableFuture}), this method will be invoked when the message listener
     * completes by returning or throwing an exception.
     *
     * <p>If the method is <b>asynchronous</b> (it returns a {@link CompletableFuture}), this method will be invoked if the message listener completes by
     * returning the {@link CompletableFuture} or by throwing an exception.
     *
     * <p>This method is guaranteed to be run on the same thread that the message listener was running on.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     */
    default void onMessageProcessingThreadComplete(MessageProcessingContext context, Message message) {}

    /**
     * Method called if the message was successfully processed and it is attempting to resolve the message.
     *
     * <p>If the message listener is <b>synchronous</b> (it does not return a {@link CompletableFuture}) and <b>does not</b> have an {@link Acknowledge}
     * argument, this method will be invoked if the message listener returns without throwing an exception and before it attempts to resolve the message.
     *
     * <p>If the method is <b>asynchronous</b> (it returns a {@link CompletableFuture}) and <b>does not</b> have an {@link Acknowledge}
     * argument, this method will be invoked if the message listener returns a completable future that is subsequently completed and before it attempts
     * to resolve the message.
     *
     * <p>If the message listener has an {@link Acknowledge} argument, this method will be invoked if the message listener calls
     * {@link Acknowledge#acknowledgeSuccessful()} before it attempts to actually resolve the method
     *
     * <p>Execution of this method is not guaranteed to be run on the same thread that the message listener was running in.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     */
    default void onMessageResolve(MessageProcessingContext context, Message message) {}

    /**
     * Method called if the message was successfully processed and the call to resolve the message was successful.
     *
     * <p>If the message listener is <b>synchronous</b> (it does not return a {@link CompletableFuture}) and <b>does not</b> have an {@link Acknowledge}
     * argument, this method will be invoked if the message listener returns without throwing an exception and the message resolving process is
     * completed successfully.
     *
     * <p>If the method is <b>asynchronous</b> (it returns a {@link CompletableFuture}) and <b>does not</b> have an {@link Acknowledge}
     * argument, this method will be invoked if the message listener returns a completable future that is subsequently completed and the message resolving
     * process is completed successfully.
     *
     * <p>If the message listener has an {@link Acknowledge} argument, this method will be invoked if the message listener calls
     * {@link Acknowledge#acknowledgeSuccessful()} and the message resolving is completed successfully.
     *
     * <p>Execution of this method is not guaranteed to be run on the same thread that the message listener was running in. Also note that
     * it is not guaranteed that either this or the {@link #onMessageResolvedFailure(MessageProcessingContext, Message, Throwable)} will be
     * executed as failure to process the message or not making a call to {@link Acknowledge} will not trigger the message resolving process to be run.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     */
    default void onMessageResolvedSuccess(MessageProcessingContext context, Message message) {}

    /**
     * Method called if the message was successfully processed but the call to resolve the message was unsuccessful.
     *
     * <p>If the message listener is <b>synchronous</b> (it does not return a {@link CompletableFuture}) and <b>does not</b> have an {@link Acknowledge}
     * argument, this method will be invoked if the message listener returns without throwing an exception and the message resolving process is
     * completed unsuccessfully.
     *
     * <p>If the method is <b>asynchronous</b> (it returns a {@link CompletableFuture}) and <b>does not</b> have an {@link Acknowledge}
     * argument, this method will be invoked if the message listener returns a completable future that is subsequently completed and the message resolving
     * process is completed unsuccessfully.
     *
     * <p>If the message listener has an {@link Acknowledge} argument, this method will be invoked if the message listener calls
     * {@link Acknowledge#acknowledgeSuccessful()} and the message resolving is completed unsuccessfully.
     *
     * <p>Execution of this method is not guaranteed to be run on the same thread that the message listener was running in. Also note that
     * it is not guaranteed that either this or the {@link #onMessageResolvedSuccess(MessageProcessingContext, Message)} will be executed
     * as failure to process the message or not making a call to {@link Acknowledge} will not trigger the message resolving process to be run.
     *
     * @param context   details about the message processing functionality, e.g. identifier for this message processor
     * @param message   the message being processed
     * @param throwable the exception that was thrown while resolving the message
     */
    default void onMessageResolvedFailure(MessageProcessingContext context, Message message, Throwable throwable) {}
}
