package com.jashmore.sqs.argument.acknowledge;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import lombok.AllArgsConstructor;

import java.util.concurrent.Future;

/**
 * Default implementation that very basically just deletes the message that we are consuming from the queue.
 */
@AllArgsConstructor
public class DefaultAcknowledge implements Acknowledge {
    private final AmazonSQSAsync amazonSqsAsync;
    private final QueueProperties queueProperties;
    private final Message message;

    @Override
    public Future<?> acknowledgeSuccessful() {
        return amazonSqsAsync.deleteMessageAsync(queueProperties.getQueueUrl(), message.getReceiptHandle());
    }
}
