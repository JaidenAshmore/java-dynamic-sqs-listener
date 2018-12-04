package com.jashmore.sqs.argument.visibility;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import lombok.AllArgsConstructor;

import java.util.concurrent.Future;

/**
 * Default implementation of the {@link VisibilityExtender} that increases the visibility of the message by sending a change message visibility request.
 *
 * @see AmazonSQSAsync#changeMessageVisibility(ChangeMessageVisibilityRequest)
 */
@AllArgsConstructor
public class DefaultVisibilityExtender implements VisibilityExtender {
    private final AmazonSQSAsync amazonSqsAsync;
    private final QueueProperties queueProperties;
    private final Message message;

    @Override
    public Future<?> extend() {
        return extend(DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS);
    }

    @Override
    public Future<?> extend(final int visibilityExtensionInSeconds) {
        return amazonSqsAsync.changeMessageVisibilityAsync(
                queueProperties.getQueueUrl(),
                message.getReceiptHandle(),
                visibilityExtensionInSeconds
        );
    }
}
