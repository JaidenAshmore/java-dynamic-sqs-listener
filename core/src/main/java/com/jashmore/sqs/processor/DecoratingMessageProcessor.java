package com.jashmore.sqs.processor;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * {@link MessageProcessor} that will decorate the processing of the message using the supplied {@link MessageProcessingDecorator}s.
 */
@Slf4j
public class DecoratingMessageProcessor implements MessageProcessor {
    private final String listenerIdentifier;
    private final QueueProperties queueProperties;
    private final List<MessageProcessingDecorator> decorators;
    private final MessageProcessor delegate;

    public DecoratingMessageProcessor(
        final String listenerIdentifier,
        final QueueProperties queueProperties,
        final List<MessageProcessingDecorator> decorators,
        final MessageProcessor delegate
    ) {
        this.listenerIdentifier = listenerIdentifier;
        this.queueProperties = queueProperties;
        this.decorators = decorators;
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<?> processMessage(final Message message, final Supplier<CompletableFuture<?>> resolveMessageCallback)
        throws MessageProcessingException {
        final MessageProcessingContext context = MessageProcessingContext
            .builder()
            .listenerIdentifier(listenerIdentifier)
            .queueProperties(queueProperties)
            .attributes(new HashMap<>())
            .build();

        decorators.forEach(
            decorator -> {
                try {
                    decorator.onPreMessageProcessing(context, message);
                } catch (RuntimeException runtimeException) {
                    throw new MessageProcessingException(runtimeException);
                }
            }
        );

        try {
            final Supplier<CompletableFuture<?>> wrappedResolveMessageCallback = () -> {
                safelyRun(decorators, decorator -> decorator.onMessageResolve(context, message));
                return resolveMessageCallback
                    .get()
                    .whenComplete(
                        (returnValue, throwable) -> {
                            if (throwable != null) {
                                safelyRun(decorators, decorator -> decorator.onMessageResolvedFailure(context, message, throwable));
                            } else {
                                safelyRun(decorators, decorator -> decorator.onMessageResolvedSuccess(context, message));
                            }
                        }
                    );
            };

            return delegate
                .processMessage(message, wrappedResolveMessageCallback)
                .whenComplete(
                    (returnValue, throwable) -> {
                        if (throwable != null) {
                            safelyRun(decorators, decorator -> decorator.onMessageProcessingFailure(context, message, throwable));
                        } else {
                            safelyRun(decorators, decorator -> decorator.onMessageProcessingSuccess(context, message, returnValue));
                        }
                    }
                );
        } catch (RuntimeException runtimeException) {
            safelyRun(decorators, decorator -> decorator.onMessageProcessingFailure(context, message, runtimeException));
            throw runtimeException;
        } finally {
            safelyRun(decorators, decorator -> decorator.onMessageProcessingThreadComplete(context, message));
        }
    }

    /**
     * Used to run the {@link MessageProcessingDecorator} methods for each of the decorators, completing all regardless of whether a previous decorator
     * failed.
     *
     * @param messageProcessingDecorators the decorators to consume
     * @param decoratorConsumer           the consumer method that would be used to run one of the decorator methods
     */
    private void safelyRun(
        final List<MessageProcessingDecorator> messageProcessingDecorators,
        final Consumer<MessageProcessingDecorator> decoratorConsumer
    ) {
        messageProcessingDecorators.forEach(
            decorator -> {
                try {
                    decoratorConsumer.accept(decorator);
                } catch (RuntimeException runtimeException) {
                    log.error("Error processing decorator: " + decorator.getClass().getSimpleName(), runtimeException);
                }
            }
        );
    }
}
