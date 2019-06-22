package com.jashmore.sqs.argument.visibility;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.Future;

/**
 * Default implementation of the {@link VisibilityExtender} that increases the visibility of the message by sending a change message visibility request.
 *
 * @see SqsAsyncClient#changeMessageVisibility(ChangeMessageVisibilityRequest)
 */
@AllArgsConstructor
public class DefaultVisibilityExtender implements VisibilityExtender {
    private final SqsAsyncClient sqsAsyncClient;
    private final QueueProperties queueProperties;
    private final Message message;

    @Override
    public Future<?> extend() {
        return extend(DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS);
    }

    @Override
    public Future<?> extend(final int visibilityExtensionInSeconds) {
        final ChangeMessageVisibilityRequest changeMessageVisibilityRequest = ChangeMessageVisibilityRequest
                .builder()
                .queueUrl(queueProperties.getQueueUrl())
                .receiptHandle(message.receiptHandle())
                .visibilityTimeout(visibilityExtensionInSeconds)
                .build();
        return sqsAsyncClient.changeMessageVisibility(changeMessageVisibilityRequest);
    }
}
