package com.jashmore.sqs.retriever.individual;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static java.lang.Math.toIntExact;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Preconditions;

import com.amazonaws.annotation.ThreadSafe;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.retriever.MessageRetriever;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
    private final AmazonSQSAsync amazonSqsAsync;
    private final QueueProperties queueProperties;
    private final IndividualMessageRetrieverProperties properties;

    @Override
    public Message retrieveMessage() throws InterruptedException {
        final Message message;
        while (true) {
            final Future<ReceiveMessageResult> receiveMessageResultFuture = amazonSqsAsync.receiveMessageAsync(generateReceiveMessageRequest());

            try {
                final ReceiveMessageResult receiveMessageResult = receiveMessageResultFuture.get();
                if (!receiveMessageResult.getMessages().isEmpty()) {
                    message = receiveMessageResult.getMessages().get(0);
                    break;
                }
            } catch (final ExecutionException executionException) {
                throw new RuntimeException("Exception retrieving message", executionException.getCause());
            }
        }

        return message;
    }

    private ReceiveMessageRequest generateReceiveMessageRequest() {
        return new ReceiveMessageRequest(queueProperties.getQueueUrl())
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .withVisibilityTimeout(properties.getVisibilityTimeoutForMessagesInSeconds());
    }
}
