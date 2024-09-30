package com.jashmore.sqs.micronaut.queue;

import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Service that is injected into the applications dependency injection framework that can be used to resolve parameterised strings to a queue url if there is
 * a queue that exists.
 */
public interface QueueResolver {
    /**
     * Resolve the queue URL for a queue.
     *
     * <p>This takes in a string that can contain parameters that can be resolved by the environment, for example properties in the
     * application.yml. If the resolved string is a URL that will be returned, otherwise it will assume it is the queue name and will call into SQS
     * to determine the queue URL for that queue.
     *
     * <p>Some example queueNameOrUrls that can be passed in are:
     * <ul>
     *     <li><code>${my.sqs.queue}</code> resolves to "myQueueName" which returns http://localhost:4576/q/myQueueName</li>
     *     <li><code>${my.sqs.queue}</code> resolves to "http://localhost:4576/q/myQueueName" and is returned</li>
     *     <li><code>http://localhost:4576/q/myQueueName</code> passed in will be returned as is</li>
     * </ul>
     *
     * @param client         the client that can be used to get information about the queue
     * @param queueNameOrUrl queueName or queueUrl that may have parameterised placeholders in it.
     * @return the resolved url of the queue if it exists
     * @throws QueueResolutionException if there was an error resolving the queue URL
     */
    String resolveQueueUrl(SqsAsyncClient client, String queueNameOrUrl) throws QueueResolutionException;
}
