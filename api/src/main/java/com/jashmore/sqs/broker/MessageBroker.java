package com.jashmore.sqs.broker;

import com.jashmore.documentation.annotations.NotThreadSafe;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Broker used to co-ordinate the retrieval and processing of messages.
 *
 * <p>If you were to consider this library as similar to a pub-sub system, this would be considered the broker/bus that transports messages from the publisher
 * to the subscriber.  For example it will ultimately be requesting messages from the {@link MessageRetriever} and delegate the processing to a
 * corresponding {@link MessageProcessor}.
 *
 * <p>The broker will process messages until either the thread is interrupted, e.g. by the calling container like the {@link MessageListenerContainer},
 * or the {@link BooleanSupplier} returns false to indicate that enough messages have been handled.
 *
 * <p>Note that retrieval of messages from the {@link MessageRetriever} is not hardcoded into the API and therefore there can be multiple ways that a
 * {@link Supplier} for message retrieval can be provided. For example, there is a batch of messages that were downloaded at some previous time and they
 * need to be processed.
 *
 * <p>The broker does not have a requirement for being thread safe as it is not intended for multiple threads to call into the
 * {@link #processMessages(ExecutorService, Supplier, Function)} methods however having it thread safe is recommended.
 *
 */
@NotThreadSafe
public interface MessageBroker {
    /**
     * Requests for messages and consume these when they are eventually obtained by the provided message {@link Consumer}.
     *
     * <p>This should keep processing messages until the {@link BooleanSupplier} returns false or the thread is interrupted. Thread
     * interruption will be triggered by the surrounding container when it is ready to stop processing messages, for example by the
     * {@link MessageListenerContainer}.
     *
     * <p>The processing of messages should be executed via the supplied {@link ExecutorService} which the container will own. This allows for the container
     * to control when the processing of messages should be interrupted.
     *
     * @param messageProcessingExecutorService the executor service that should be used for every message being processed
     * @param keepProcessingMessages           function to determine whether we should stop processing messages
     * @param messageSupplier                  the function to request for a new message to process
     * @param messageProcessor                 the function that will process these messages when it is obtained
     * @throws InterruptedException when the container has requested the processing of messages to stop
     */
    void processMessages(ExecutorService messageProcessingExecutorService,
                         BooleanSupplier keepProcessingMessages,
                         Supplier<CompletableFuture<Message>> messageSupplier,
                         Function<Message, CompletableFuture<?>> messageProcessor) throws InterruptedException;

    /**
     * Requests for messages and consume these when they are eventually obtained by the provided message {@link Consumer}.
     *
     * <p>This should keep processing messages until the the thread is interrupted. Thread interruption will be triggered by the surrounding container
     * when it is ready to stop processing messages, for example by the {@link MessageListenerContainer}.
     *
     * <p>The processing of messages should be executed via the supplied {@link ExecutorService} which the container will own. This allows for the container
     * to control when the processing of messages should be interrupted.
     *
     * @param messageProcessingExecutorService the executor service that should be used for every message being processed
     * @param messageSupplier                  the function to request for a new message to process
     * @param messageProcessor                 the function that will process these messages when it is obtained
     * @throws InterruptedException when the container has requested the processing of messages to stop
     */
    default void processMessages(ExecutorService messageProcessingExecutorService,
                                 Supplier<CompletableFuture<Message>> messageSupplier,
                                 Function<Message, CompletableFuture<?>> messageProcessor) throws InterruptedException {
        processMessages(messageProcessingExecutorService, () -> true, messageSupplier, messageProcessor);
    }
}
