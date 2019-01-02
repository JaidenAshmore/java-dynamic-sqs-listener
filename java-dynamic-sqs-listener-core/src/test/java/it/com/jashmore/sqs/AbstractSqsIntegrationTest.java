package it.com.jashmore.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES;
import static software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public abstract class AbstractSqsIntegrationTest {
    private static final int MAX_SEND_MESSAGE_BATCH_SIZE = 10;

    public static void assertNoMessagesInQueue(final SqsAsyncClient sqsAsyncClient,
                                               final String queueUrl) {
        GetQueueAttributesRequest request = GetQueueAttributesRequest
                .builder()
                .queueUrl(queueUrl)
                .attributeNames(APPROXIMATE_NUMBER_OF_MESSAGES, APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                .build();
        sqsAsyncClient.getQueueAttributes(request)
                .handle((queueState, error) -> {
                    if (error != null) {
                        throw new RuntimeException(error);
                    }

                    assertThat(queueState.attributes().get(APPROXIMATE_NUMBER_OF_MESSAGES)).isEqualTo("0");
                    assertThat(queueState.attributes().get(APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).isEqualTo("0");
                    return null;
                });
    }

    public static void sendNumberOfMessages(int numberOfMessages,
                                            final SqsAsyncClient sqsAsyncClient,
                                            final String queueUrl) {
        int numberOfMessagesSent = 0;
        while (numberOfMessagesSent < numberOfMessages) {
            final SendMessageBatchRequest.Builder sendMessageBatchRequestBuilder = SendMessageBatchRequest.builder().queueUrl(queueUrl);
            final int batchSize = Math.min(numberOfMessages - numberOfMessagesSent, MAX_SEND_MESSAGE_BATCH_SIZE);
            sendMessageBatchRequestBuilder.entries(IntStream.range(0, batchSize)
                    .map(index -> numberOfMessages + index)
                    .mapToObj(id -> SendMessageBatchRequestEntry.builder().id("" + id).messageBody("body: " + id).build())
                    .collect(Collectors.toSet()));
            try {
                sqsAsyncClient.sendMessageBatch(sendMessageBatchRequestBuilder.build()).get();
                log.debug("Sent {} messages to the queue", batchSize);
            } catch (InterruptedException | ExecutionException exception) {
                throw new RuntimeException(exception);
            }

            numberOfMessagesSent += batchSize;
        }
    }
}
