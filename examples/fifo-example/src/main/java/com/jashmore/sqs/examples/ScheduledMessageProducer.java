package com.jashmore.sqs.examples;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

@Slf4j
@Component
public class ScheduledMessageProducer {
    private final SqsAsyncClient sqsAsyncClient;
    private final AtomicInteger count;
    private final String queueUrl;

    private ScheduledMessageProducer(final SqsAsyncClient sqsAsyncClient) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.count = new AtomicInteger();

        try {
            this.queueUrl = sqsAsyncClient.getQueueUrl(request -> request.queueName("test-queue.fifo")).get().queueUrl();
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error determing queue URL");
        } catch (final ExecutionException executionException) {
            throw new RuntimeException(executionException);
        }
    }

    @Scheduled(initialDelay = 3000, fixedDelay = 3000)
    public void addMessages() {
        log.info("Putting 10 messages onto each queue");
        final int currentValue = count.incrementAndGet();

        final SendMessageBatchRequest.Builder batchRequestBuilder = SendMessageBatchRequest
            .builder()
            .queueUrl(queueUrl)
            .entries(
                IntStream
                    .range(0, 10)
                    .mapToObj(
                        i -> {
                            final String messageId = "" + currentValue + "-" + i;
                            return SendMessageBatchRequestEntry
                                .builder()
                                .id(messageId)
                                .messageBody(String.valueOf(currentValue))
                                .messageGroupId("" + i)
                                .messageDeduplicationId(messageId)
                                .build();
                        }
                    )
                    .collect(Collectors.toSet())
            );

        sqsAsyncClient.sendMessageBatch(batchRequestBuilder.build());

        final SendMessageBatchRequest.Builder secondBatchGroup = SendMessageBatchRequest
            .builder()
            .queueUrl(queueUrl)
            .entries(
                IntStream
                    .range(0, 10)
                    .mapToObj(
                        i -> {
                            final String messageId = "" + currentValue + "-" + (10 + i);
                            return SendMessageBatchRequestEntry
                                .builder()
                                .id(messageId)
                                .messageBody(String.valueOf(currentValue))
                                .messageGroupId("" + (10 + i))
                                .messageDeduplicationId(messageId)
                                .build();
                        }
                    )
                    .collect(Collectors.toSet())
            );

        sqsAsyncClient.sendMessageBatch(secondBatchGroup.build());
        // TODO: Investigate why order isn't being maintained when multiple messages in the same group are sent, I think this is an ElasticMQ bug
        //        final SendMessageBatchRequest.Builder groupWithCoupleMoreMessages = SendMessageBatchRequest
        //                .builder()
        //                .queueUrl(queueUrl)
        //                .entries(
        //                        IntStream
        //                                .range(0, 10)
        //                                .mapToObj(
        //                                        i -> {
        //                                            final String messageId = "individual-" + currentValue + "-" + i;
        //                                            final String messageBody = String.valueOf((currentValue - 1) * 10 + i);
        //                                            log.info("Adding message for group 21: {} body: {}", messageId, messageBody);
        //                                            return SendMessageBatchRequestEntry
        //                                                    .builder()
        //                                                    .id(messageId)
        //                                                    .messageBody(messageBody)
        //                                                    .messageGroupId("21")
        //                                                    .messageDeduplicationId(messageId)
        //                                                    .build();
        //                                        }
        //                                )
        //                                .collect(Collectors.toSet())
        //                );
        //
        //
        //        sqsAsyncClient.sendMessageBatch(groupWithCoupleMoreMessages.build());

        //        log.info("Messages in queue: {}", sqsAsyncClient.getQueueAttributes(builder -> builder.queueUrl(queueUrl).attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)).get()
        //                .attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }
}
