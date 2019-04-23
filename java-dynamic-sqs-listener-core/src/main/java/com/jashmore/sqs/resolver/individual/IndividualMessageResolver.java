package com.jashmore.sqs.resolver.individual;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.resolver.MessageResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;

/**
 * {@link MessageResolver} that will resolve the message immediately by triggering the deletion of the message on the queue.
 */
@Slf4j
@AllArgsConstructor
public class IndividualMessageResolver implements MessageResolver {
    private final QueueProperties queueProperties;
    private final SqsAsyncClient sqsAsyncClient;

    @Override
    public CompletableFuture<?> resolveMessage(final Message message) {
        final DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(queueProperties.getQueueUrl())
                .receiptHandle(message.receiptHandle())
                .build();

        return sqsAsyncClient.deleteMessage(deleteMessageRequest)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        log.error("Error deleting message: " + message.messageId(), throwable);
                        return;
                    }

                    log.debug("Message successfully deleted: {}", message.messageId());
                });
    }
}
