package com.jashmore.sqs.client;

import java.util.concurrent.ExecutionException;

import com.jashmore.sqs.placeholder.PlaceholderResolver;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Default implementation that allows placeholders to be included in the string that will be replaced before
 * calling out to the SQS server to get the Queue URL.
 */
public class DefaultPlaceholderQueueResolver implements QueueResolver {

    private final PlaceholderResolver placeholderResolver;

    public DefaultPlaceholderQueueResolver(final PlaceholderResolver placeholderResolver) {
        this.placeholderResolver = placeholderResolver;
    }

    @Override
    public String resolveQueueUrl(final SqsAsyncClient sqsAsyncClient, final String queueNameOrUrl) {
        final String resolvedQueueNameOrUrl = placeholderResolver.resolvePlaceholders(queueNameOrUrl);

        if (resolvedQueueNameOrUrl.startsWith("http")) {
            return resolvedQueueNameOrUrl;
        }

        try {
            return sqsAsyncClient.getQueueUrl(builder -> builder.queueName(resolvedQueueNameOrUrl)).get().queueUrl();
        } catch (ExecutionException executionException) {
            throw new QueueResolutionException(executionException.getCause());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new QueueResolutionException(interruptedException);
        }
    }
}
