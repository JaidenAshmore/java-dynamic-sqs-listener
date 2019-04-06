package com.jashmore.sqs.processor.resolver.individual;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.processor.resolver.MessageResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.ExecutionException;

/**
 * {@link MessageResolver} that will resolve the message immediately by trigger the deletion of the message on the queue.
 */
@Slf4j
@AllArgsConstructor
public class IndividualMessageResolver implements MessageResolver {
    private final QueueProperties queueProperties;
    private final SqsAsyncClient sqsAsyncClient;

    @Override
    public void resolveMessage(final Message message) {
        final DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(queueProperties.getQueueUrl())
                .receiptHandle(message.receiptHandle())
                .build();

        try {
            sqsAsyncClient.deleteMessage(deleteMessageRequest)
                    .get();
            log.debug("Message successfully deleted: {}", message.messageId());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException exception) {
            log.error("Error deleting message: " + message.messageId(), exception);
        }
    }
}
