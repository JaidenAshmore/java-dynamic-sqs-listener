package com.jashmore.sqs.resolver.blocking;

import com.jashmore.sqs.resolver.MessageResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Implementation that will delegate to an underlying {@link MessageResolver} for the resolution but for every message that is being resolved it will block
 * the thread and wait for the message to be processed.
 */
@Slf4j
@AllArgsConstructor
public class BlockingMessageResolver implements MessageResolver {
    private final MessageResolver delegate;

    @Override
    public CompletableFuture<?> resolveMessage(final Message message) {
        final CompletableFuture<?> messageResolvedCompletableFuture = delegate.resolveMessage(message);

        try {
            messageResolvedCompletableFuture.get();
        } catch (final InterruptedException interruptedException) {
            // Re-interrupt the thread for the consumer to deal with
            Thread.currentThread().interrupt();
        } catch (final ExecutionException executionException) {
            // ignore, up to the consumer to deal with as the future as this future is being returned
        }

        return messageResolvedCompletableFuture;
    }
}
