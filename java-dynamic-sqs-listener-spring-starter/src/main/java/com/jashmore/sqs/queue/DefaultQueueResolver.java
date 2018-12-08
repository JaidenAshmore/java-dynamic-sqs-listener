package com.jashmore.sqs.queue;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import lombok.AllArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Default implementation that uses the {@link Environment} to resolve the placeholders in a string and from that either
 * return the URL if it is a URL or uses the value to try and get the URL from the SQS server.
 */
@Service
@AllArgsConstructor
public class DefaultQueueResolver implements QueueResolver {
    private final AmazonSQSAsync amazonSqsAsync;
    private final Environment environment;

    @Override
    public String resolveQueueUrl(final String queueNameOrUrl) {
        final String resolvedQueueNameOrUrl = environment.resolveRequiredPlaceholders(queueNameOrUrl);

        if (resolvedQueueNameOrUrl.startsWith("http")) {
            return resolvedQueueNameOrUrl;
        }

        return amazonSqsAsync.getQueueUrl(resolvedQueueNameOrUrl).getQueueUrl();
    }
}
