package com.jashmore.sqs.decorator;

import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.resolver.MessageResolver;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Used to decorate the thread that will be used to process the given message.
 *
 * <p>This can be used to add extra information to the thread, such as Tracing, metrics, logging, etc.
 *
 * <p>For context on which each types of events that can be decorated, please take a look at the following key words:
 * <ul>
 *     <li><b>supply</b>: this represents the {@link MessageProcessor} handing (supplying) the message to the message listener to process the
 *     message. Once the message listener function returns the supply task is deemed completed.
 *     <li><b>processing</b>: this represents the actual logic the performs the message processing. If this process completes successfully it indicates that
 *     the message listener succeeded and it should attempt to be resolved, however if it is completed unsuccessfully the message will not be resolved.
 *     <li><b>resolve</b>: resolving a message represents the attempt to mark the message as completed successfully, e.g. calling to
 *     {@link MessageResolver#resolveMessage(Message)}.
 * </ul>
 */
@SuppressWarnings("unused")
@ParametersAreNonnullByDefault
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
    default void onPreSupply(MessageProcessingContext context, Message message) {

    }

    /**
     * Method called if the message listener threw an exception while supplying the message.
     *
     * <p>If the message listener is synchronous, both this and {@link #onMessageProcessingFailure(MessageProcessingContext, Message, Throwable)}
     * will be called with the same exception.
     *
     * <p>This is guaranteed to be performed on the same thread as the message processing thread and will run before
     * the {@link #onSupplyFinished(MessageProcessingContext, Message)} callback.
     *
     * @param context   details about the message processing functionality, e.g. identifier for this message processor
     * @param message   the message being processed
     * @param throwable exception that was thrown by the message listener
     */
    default void onSupplyFailure(MessageProcessingContext context, Message message, Throwable throwable) {

    }

    /**
     * Method called if the message processing thread did not throw an exception when supplying the message.
     *
     * <p>Note that this does not guarantee that the message resolving was actually completed as it could have returned a {@link CompletableFuture} indicating
     * asynchronous processing or if the message consumer has the {@link Acknowledge} parameter and therefore controlling when it is a success.
     *
     * <p>For listening to when the message has been successfully processed, take a look at the
     * {@link #onMessageProcessingSuccess(MessageProcessingContext, Message, Object)} method which will be called when the message
     * was actually resolved via the {@link MessageResolver#resolveMessage(Message)} call.
     *
     * <p>This is guaranteed to be performed on the same thread as the message processing thread and will run before
     * the {@link #onSupplyFinished(MessageProcessingContext, Message)} callback. If the message has been attempted to be supplied, either this method
     * or {@link #onSupplyFailure(MessageProcessingContext, Message, Throwable)} are guaranteed to be run for the message.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     */
    default void onSupplySuccess(MessageProcessingContext context, Message message) {

    }

    /**
     * Method called when the message was successfully handed to the message listener for processing.
     *
     * <p>This does not guarantee that the message has finished processing as it could have been run on an asynchronous thread
     * that is resolved independently or the message consumer will resolve the thread manually using the {@link Acknowledge}
     * parameter.
     *
     * <p>If the message listener has started to process the message, this is guaranteed to be run regardless of the state of the message processing
     * and will be executed on the same thread that was processing the message. Note that, if any of the
     * {@link #onPreSupply(MessageProcessingContext, Message)} methods failed, the message listener will not be called and therefore this method
     * will not be run.
     *
     * <p>This is guaranteed to be performed on the same thread as the message processing thread.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     */
    default void onSupplyFinished(MessageProcessingContext context, Message message) {

    }

    /**
     * Method called when the future for the message processing was completed exceptionally.
     *
     * <p>This will triggered if the message listener method returns a {@link CompletableFuture} that ends up being completed exceptionally. Another way
     * is if the method completes successfully (synchronous or asynchronous), there is no {@link Acknowledge} argument and the subsequent call to
     * {@link MessageResolver#resolveMessage(Message)} fails. In this case both this method and
     * {@link #onMessageResolvedFailure(MessageProcessingContext, Message, Throwable)} will be called.
     *
     * <p>This will not be run on the thread that was processing the message.
     *
     * @param context   details about the message processing functionality, e.g. identifier for this message processor
     * @param message   the message being processed
     * @param throwable the exception thrown
     */
    default void onMessageProcessingFailure(MessageProcessingContext context, Message message, Throwable throwable) {

    }

    /**
     * Method called when the message has successfully been processed.
     *
     * <p>This may or may not align with {@link #onSupplySuccess(MessageProcessingContext, Message)} if message listener is synchronous. However,
     * the divergence for these methods occurs when the message listener processes the message asynchronously and
     * this will be triggered once that asynchronous process is resolved, compared to {@link #onSupplySuccess(MessageProcessingContext, Message)} which
     * is called when the message listener returns the {@link CompletableFuture}.
     *
     * <p>This will not be run on the thread that was processing the message.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     * @param object  the value that was resolved from the message listener
     */
    default void onMessageProcessingSuccess(MessageProcessingContext context, Message message, Object object) {

    }

    /**
     * Method called when the message has completed, regardless of the result.
     *
     * <p>This may or may not align with {@link #onSupplyFinished(MessageProcessingContext, Message)} if message listener is synchronous. However,
     * the divergence for these methods occurs when the message listener processes the message asynchronously and
     * this will be triggered once that asynchronous process is resolved, compared to {@link #onSupplySuccess(MessageProcessingContext, Message)} which
     * is called when the message listener returns the {@link CompletableFuture}.
     *
     * <p>This will not be run on the thread that was processing the message.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     */
    default void onMessageProcessingFinished(MessageProcessingContext context, Message message) {

    }

    /**
     * Method called if the message was successfully processed and the call to resolve the message was successful.
     *
     * <p>This will not be run on the thread that was processing the message.
     *
     * @param context details about the message processing functionality, e.g. identifier for this message processor
     * @param message the message being processed
     */
    default void onMessageResolvedSuccess(MessageProcessingContext context, Message message) {

    }

    /**
     * Method called if the message was was successfully processed and the attempt to resolve the message failed.
     *
     * <p>This can be useful in monitoring the underlying infrastructure failures for the message being successfully processed but it couldn't
     * be removed from the SQS queue and therefore will be processed again if there is replaying setup. This could indicate wasted duplicate effort.
     *
     * @param context   details about the message processing functionality, e.g. identifier for this message processor
     * @param message   the message being processed
     * @param throwable exception that was thrown by the {@link MessageResolver}
     */
    default void onMessageResolvedFailure(MessageProcessingContext context, Message message, Throwable throwable) {

    }
}
