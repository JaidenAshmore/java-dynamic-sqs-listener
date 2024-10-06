package com.jashmore.sqs.examples;

import com.jashmore.sqs.container.MessageListenerContainerCoordinator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MessageListenerContainerCoordinator containerCoordinator;

    @Autowired
    private ScheduledMessageProducer(final SqsAsyncClient sqsAsyncClient, final MessageListenerContainerCoordinator containerCoordinator) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.containerCoordinator = containerCoordinator;
        this.count = new AtomicInteger();

        try {
            this.queueUrl = sqsAsyncClient.getQueueUrl(request -> request.queueName("test-queue.fifo")).get().queueUrl();
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error determining queue URL");
        } catch (final ExecutionException executionException) {
            throw new RuntimeException(executionException);
        }
    }

    @Scheduled(initialDelay = 20_000, fixedDelay = 20_000)
    public void restartContainer() throws Exception {
        log.info("Container stopping");
        containerCoordinator.stopContainer("fifo");

        log.info("Waiting to turn container back on");
        Thread.sleep(5_000);

        containerCoordinator.startContainer("fifo");
        log.info("Container started again");
    }

    @Scheduled(initialDelay = 3000, fixedDelay = 3000)
    public void addMessages() {
        log.info("Putting 30 messages onto each queue");
        final int currentValue = count.incrementAndGet();

        final SendMessageBatchRequest.Builder batchRequestBuilder = SendMessageBatchRequest
            .builder()
            .queueUrl(queueUrl)
            .entries(
                IntStream
                    .range(0, 10)
                    .mapToObj(i -> {
                        final String messageId = "" + currentValue + "-" + i;
                        return SendMessageBatchRequestEntry
                            .builder()
                            .id(messageId)
                            .messageBody(String.valueOf(currentValue))
                            .messageGroupId("" + i)
                            .messageDeduplicationId(messageId)
                            .build();
                    })
                    .collect(Collectors.toList())
            );

        sqsAsyncClient.sendMessageBatch(batchRequestBuilder.build());

        final SendMessageBatchRequest.Builder secondBatchGroup = SendMessageBatchRequest
            .builder()
            .queueUrl(queueUrl)
            .entries(
                IntStream
                    .range(10, 20)
                    .mapToObj(i -> {
                        final String messageId = "" + currentValue + "-" + i;
                        return SendMessageBatchRequestEntry
                            .builder()
                            .id(messageId)
                            .messageBody(String.valueOf(currentValue))
                            .messageGroupId("" + i)
                            .messageDeduplicationId(messageId)
                            .build();
                    })
                    .collect(Collectors.toList())
            );

        sqsAsyncClient.sendMessageBatch(secondBatchGroup.build());
        final SendMessageBatchRequest.Builder groupWithCoupleMoreMessages = SendMessageBatchRequest
            .builder()
            .queueUrl(queueUrl)
            .entries(
                IntStream
                    .range(0, 10)
                    .mapToObj(i -> {
                        final String messageId = "individual-" + currentValue + "-" + i;
                        final String messageBody = String.valueOf((currentValue - 1) * 10 + i + 1);
                        return SendMessageBatchRequestEntry
                            .builder()
                            .id(messageId)
                            .messageBody(messageBody)
                            .messageGroupId("21")
                            .messageDeduplicationId(messageId)
                            .build();
                    })
                    .collect(Collectors.toList())
            );

        sqsAsyncClient.sendMessageBatch(groupWithCoupleMoreMessages.build());
    }
}
