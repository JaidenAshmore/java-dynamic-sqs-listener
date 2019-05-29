package com.jashmore.sqs.retriever.individual;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.retriever.MessageRetriever;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
        final Message message;
        while (true) {
            final Future<ReceiveMessageResponse> receiveMessageResultFuture = sqsAsyncClient.receiveMessage(generateReceiveMessageRequest());

            try {
                final ReceiveMessageResponse response = receiveMessageResultFuture.get();
                if (!response.messages().isEmpty()) {
                    message = response.messages().get(0);
                    break;
                }
            } catch (final ExecutionException executionException) {
                throw new RuntimeException("Exception retrieving message", executionException.getCause());
            }
        }

        return message;
    }

    private ReceiveMessageRequest generateReceiveMessageRequest() {
        return ReceiveMessageRequest
                .builder()
                .queueUrl(queueProperties.getQueueUrl())
                .maxNumberOfMessages(1)
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames(QueueAttributeName.ALL.toString())
                .waitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .visibilityTimeout(properties.getVisibilityTimeoutForMessagesInSeconds())
                .build();
    }
}
