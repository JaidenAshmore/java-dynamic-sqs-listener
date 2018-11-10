package it.com.jashmore.sqs;

import static com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessages;
import static com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessagesNotVisible;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.jashmore.sqs.util.Immutables;

public abstract class AbstractSqsIntegrationTest {
    private static final int MAX_SEND_MESSAGE_BATCH_SIZE = 10;

    public static void assertNoMessagesInQueue(final AmazonSQSAsync amazonSqsAsync,
                                                final String queueUrl) {
        final GetQueueAttributesResult queueState = amazonSqsAsync.getQueueAttributes(queueUrl, Immutables.immutableList(
                ApproximateNumberOfMessages.name(), ApproximateNumberOfMessagesNotVisible.name()));
        assertThat(queueState.getAttributes().get(ApproximateNumberOfMessages.name())).isEqualTo("0");
        assertThat(queueState.getAttributes().get(ApproximateNumberOfMessagesNotVisible.name())).isEqualTo("0");
    }

    public static void sendNumberOfMessages(int numberOfMessages,
                                      final AmazonSQSAsync amazonSqsAsync,
                                      final String queueUrl) {
        int numberOfMessagesSent = 0;
        while (numberOfMessagesSent < numberOfMessages) {
            final SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest(queueUrl);
            final int batchSize = Math.min(numberOfMessages - numberOfMessagesSent, MAX_SEND_MESSAGE_BATCH_SIZE);
            for (int j = 0; j < batchSize; ++j) {
                sendMessageBatchRequest.withEntries(new SendMessageBatchRequestEntry("" + j, "body"));
            }
            amazonSqsAsync.sendMessageBatch(sendMessageBatchRequest);

            numberOfMessagesSent += batchSize;
        }
    }
}
