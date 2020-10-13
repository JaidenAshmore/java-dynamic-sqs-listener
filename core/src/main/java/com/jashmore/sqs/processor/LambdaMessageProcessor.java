package com.jashmore.sqs.processor;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.visibility.DefaultVisibilityExtender;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * {@link MessageProcessor} that takes a lambda/function for synchronous processing of a message.
 */
@Slf4j
public class LambdaMessageProcessor implements MessageProcessor {

    private final SqsAsyncClient sqsAsyncClient;
    private final QueueProperties queueProperties;

    private final boolean usesAcknowledgeParameter;
    private final MessageProcessingFunction messageProcessingFunction;

    /**
     * Constructor.
     *
     * @param sqsAsyncClient   the client to communicate with SQS
     * @param queueProperties  the properties of the queue
     * @param messageProcessor the function to consume a message and return the future
     */
    public LambdaMessageProcessor(
        final SqsAsyncClient sqsAsyncClient,
        final QueueProperties queueProperties,
        final Consumer<Message> messageProcessor
    ) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.queueProperties = queueProperties;

        this.usesAcknowledgeParameter = false;
        this.messageProcessingFunction = (message, acknowledge, visibilityExtender) -> messageProcessor.accept(message);
    }

    /**
     * Constructor.
     *
     * @param sqsAsyncClient   the client to communicate with SQS
     * @param queueProperties  the properties of the queue
     * @param messageProcessor the function to consume a message and acknowledge
     */
    public LambdaMessageProcessor(
        final SqsAsyncClient sqsAsyncClient,
        final QueueProperties queueProperties,
        final BiConsumer<Message, Acknowledge> messageProcessor
    ) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.queueProperties = queueProperties;

        this.usesAcknowledgeParameter = true;
        this.messageProcessingFunction = (message, acknowledge, visibilityExtender) -> messageProcessor.accept(message, acknowledge);
    }

    /**
     * Constructor.
     *
     * <p>As Java generics has type erasure and will convert <code>BiFunction&lt;A, B, C&gt;</code> to <code>BiFunction</code> we need to change
     * the type signature to distinguish the function that consumes a message and an acknowledge compared to the function that consumes a message
     * and a visibility extender. As the visibility extender use case seems less common, this one has the unused parameter.
     *
     * @param sqsAsyncClient        the client to communicate with SQS
     * @param queueProperties       the properties of the queue
     * @param ignoredForTypeErasure field needed due to type erasure
     * @param messageProcessor      the function to consume a message and visibility extender and return the future
     */
    public LambdaMessageProcessor(
        final SqsAsyncClient sqsAsyncClient,
        final QueueProperties queueProperties,
        @SuppressWarnings("unused") final boolean ignoredForTypeErasure,
        final BiConsumer<Message, VisibilityExtender> messageProcessor
    ) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.queueProperties = queueProperties;

        this.usesAcknowledgeParameter = false;
        this.messageProcessingFunction = (message, acknowledge, visibilityExtender) -> messageProcessor.accept(message, visibilityExtender);
    }

    /**
     * Constructor.
     *
     * @param sqsAsyncClient   the client to communicate with SQS
     * @param queueProperties  the properties of the queue
     * @param messageProcessor the function to consume a message, acknowledge and visibility extender and return the future
     */
    public LambdaMessageProcessor(
        final SqsAsyncClient sqsAsyncClient,
        final QueueProperties queueProperties,
        final MessageProcessingFunction messageProcessor
    ) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.queueProperties = queueProperties;

        this.usesAcknowledgeParameter = true;
        this.messageProcessingFunction = messageProcessor;
    }

    @Override
    public CompletableFuture<?> processMessage(Message message, Supplier<CompletableFuture<?>> resolveMessageCallback) {
        final Acknowledge acknowledge = resolveMessageCallback::get;
        final VisibilityExtender visibilityExtender = new DefaultVisibilityExtender(sqsAsyncClient, queueProperties, message);
        try {
            messageProcessingFunction.processMessage(message, acknowledge, visibilityExtender);
        } catch (final MessageProcessingException messageProcessingException) {
            return CompletableFutureUtils.completedExceptionally(messageProcessingException);
        } catch (final RuntimeException runtimeException) {
            return CompletableFutureUtils.completedExceptionally(new MessageProcessingException(runtimeException));
        }

        if (usesAcknowledgeParameter) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture
            .completedFuture(null)
            .thenAccept(
                ignored -> {
                    try {
                        resolveMessageCallback
                            .get()
                            .handle(
                                (i, throwable) -> {
                                    if (throwable != null) {
                                        log.error("Error resolving successfully processed message", throwable);
                                    }
                                    return null;
                                }
                            );
                    } catch (final RuntimeException runtimeException) {
                        log.error("Failed to trigger message resolving", runtimeException);
                    }
                }
            );
    }

    /**
     * Represents a message processing function that consumes the {@link Message}, {@link Acknowledge} and {@link VisibilityExtender}.
     */
    @FunctionalInterface
    public interface MessageProcessingFunction {
        void processMessage(Message message, Acknowledge acknowledge, VisibilityExtender visibilityExtender);
    }
}
