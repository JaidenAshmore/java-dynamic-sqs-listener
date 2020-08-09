package com.jashmore.sqs.spring.queue;

import java.util.concurrent.ExecutionException;
import lombok.AllArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Default implementation that uses the {@link Environment} to resolve the placeholders in a string and from that either
 * return the URL if it is a URL or uses the value to try and get the URL from the SQS server.
 */
@Service
@AllArgsConstructor
public class DefaultQueueResolver implements QueueResolver {
    private final Environment environment;

    @Override
    public String resolveQueueUrl(final SqsAsyncClient sqsAsyncClient, final String queueNameOrUrl) {
        final String resolvedQueueNameOrUrl = environment.resolveRequiredPlaceholders(queueNameOrUrl);

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
