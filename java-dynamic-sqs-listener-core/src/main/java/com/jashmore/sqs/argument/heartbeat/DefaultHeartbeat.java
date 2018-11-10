package com.jashmore.sqs.argument.heartbeat;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import lombok.AllArgsConstructor;

import java.util.concurrent.Future;

/**
 * Default implementation of the {@link Heartbeat} that increases the visibility of the message by sending a change message visibility request.
 *
 * @see AmazonSQSAsync#changeMessageVisibility(ChangeMessageVisibilityRequest)
 */
@AllArgsConstructor
public class DefaultHeartbeat implements Heartbeat {
    private final AmazonSQSAsync amazonSqsAsync;
    private final QueueProperties queueProperties;
    private final Message message;

    @Override
    public Future<?> beat() {
        return beat(DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS);
    }

    @Override
    public Future<?> beat(final int visibilityExtensionInSeconds) {
        return amazonSqsAsync.changeMessageVisibilityAsync(
                queueProperties.getQueueUrl(),
                message.getReceiptHandle(),
                visibilityExtensionInSeconds
        );
    }
}
