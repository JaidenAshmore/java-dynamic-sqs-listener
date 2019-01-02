package com.jashmore.sqs.argument.acknowledge;

import com.jashmore.sqs.QueueProperties;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.Future;

/**
 * Default implementation that very basically just deletes the message that we are consuming from the queue.
 */
@AllArgsConstructor
public class DefaultAcknowledge implements Acknowledge {
    private final SqsAsyncClient sqsAsyncClient;
    private final QueueProperties queueProperties;
    private final Message message;

    @Override
    public Future<?> acknowledgeSuccessful() {
        final DeleteMessageRequest deleteRequest = DeleteMessageRequest
                .builder()
                .queueUrl(queueProperties.getQueueUrl())
                .receiptHandle(message.receiptHandle())
                .build();
        return sqsAsyncClient.deleteMessage(deleteRequest);
    }
}
