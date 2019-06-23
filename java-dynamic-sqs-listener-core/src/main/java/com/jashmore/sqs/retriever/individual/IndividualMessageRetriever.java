package com.jashmore.sqs.retriever.individual;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverConstants.DEFAULT_BACKOFF_TIME_IN_MS;

import com.google.common.annotations.VisibleForTesting;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.properties.PropertyUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkInterruptedException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.ExecutionException;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Message retriever that will obtain a new message from the server as requested and will not prefetch any messages for future use.
 *
 * <p>This can be useful for when processing of messages can take a long time and it is undesirable to prefetch them as that could result in the
 * prefetched messages visibility timeout expiring.
 */
@Slf4j
@ThreadSafe
@AllArgsConstructor
public class IndividualMessageRetriever implements MessageRetriever {
    private final SqsAsyncClient sqsAsyncClient;
    private final QueueProperties queueProperties;
    private final IndividualMessageRetrieverProperties properties;

    @Override
    public Message retrieveMessage() throws InterruptedException {
        while (true) {
            final ReceiveMessageResponse response;
            try {
                response = sqsAsyncClient.receiveMessage(generateReceiveMessageRequest()).get();
                if (response.messages().isEmpty()) {
                    continue;
                }

                return response.messages().get(0);
            } catch (final ExecutionException | RuntimeException exception) {
                if (exception instanceof ExecutionException) {
                    // Supposedly the SqsAsyncClient can get interrupted and this will remove the interrupted status from the thread and then wrap it
                    // in it's own version of the interrupted exception...If this happens when the retriever is being shut down it will keep on processing
                    // because it does not realise it is being shut down, therefore we have to check for this and quit if necessary
                    final Throwable executionExceptionCause = exception.getCause();
                    if (executionExceptionCause instanceof SdkClientException) {
                        if (executionExceptionCause.getCause() instanceof SdkInterruptedException) {
                            log.debug("Thread interrupted while receiving messages");
                            throw new InterruptedException("Interrupted while retrieving messages");
                        }
                    }
                }

                final long errorBackoffTimeInMilliseconds = getErrorBackoffTimeInMilliseconds();
                log.error("Error thrown while organising threads to process messages. Backing off for {}ms", errorBackoffTimeInMilliseconds, exception);
                backoff(errorBackoffTimeInMilliseconds);
            }
        }
    }

    /**
     * Get the number of seconds that the thread should wait when there was an error trying to organise a thread to process.
     *
     * @return the backoff time in milliseconds
     * @see ConcurrentMessageBrokerProperties#getErrorBackoffTimeInMilliseconds() for more information
     */
    private long getErrorBackoffTimeInMilliseconds() {
        return PropertyUtils.safelyGetPositiveOrZeroLongValue(
                "errorBackoffTimeInMilliseconds",
                properties::getErrorBackoffTimeInMilliseconds,
                DEFAULT_BACKOFF_TIME_IN_MS
        );
    }

    @VisibleForTesting
    void backoff(final long backoffTimeInMs) throws InterruptedException {
        Thread.sleep(backoffTimeInMs);
    }

    private ReceiveMessageRequest generateReceiveMessageRequest() {
        final ReceiveMessageRequest.Builder requestBuilder = ReceiveMessageRequest.builder()
                .queueUrl(queueProperties.getQueueUrl())
                .maxNumberOfMessages(1)
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames(QueueAttributeName.ALL.toString())
                .waitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
        final Integer visibilityTimeoutInSeconds = properties.getMessageVisibilityTimeoutInSeconds();
        if (visibilityTimeoutInSeconds != null) {
            if (visibilityTimeoutInSeconds <= 0) {
                log.warn("Non-positive visibilityTimeoutInSeconds provided: {}", visibilityTimeoutInSeconds);
            } else {
                requestBuilder.visibilityTimeout(visibilityTimeoutInSeconds);
            }
        }

        return requestBuilder.build();
    }
}
