package com.jashmore.sqs.examples;

import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

/**
 * Helper scheduled task that will place 10 messages onto each queue for processing by the message listeners.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMessageProducer {

    private final SqsAsyncClientProvider sqsAsyncClientProvider;
    private final AtomicInteger count = new AtomicInteger();

    /**
     * Scheduled job that sends messages to the queue for testing the listener.
     *
     * @throws Exception if there was an error placing messages on the queue
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 1000)
    public void addMessages() throws Exception {
        log.info("Putting 10 messages onto each queue");
        final int currentValue = count.incrementAndGet();

        sendMessagesToQueue(getSqsAsyncClient("firstClient"), "firstClientQueue", currentValue);
        sendMessagesToQueue(getSqsAsyncClient("secondClient"), "secondClientQueue", currentValue);
    }

    private SqsAsyncClient getSqsAsyncClient(final String clientId) {
        return sqsAsyncClientProvider.getClient(clientId).orElseThrow(() -> new RuntimeException("Unknown client: " + clientId));
    }

    private void sendMessagesToQueue(final SqsAsyncClient sqsAsyncClient, final String queueName, final int currentValue)
        throws ExecutionException, InterruptedException {
        final String queueUrl = sqsAsyncClient.getQueueUrl(request -> request.queueName(queueName)).get().queueUrl();

        final SendMessageBatchRequest.Builder batchRequestBuilder = SendMessageBatchRequest.builder().queueUrl(queueUrl);
        batchRequestBuilder.entries(
            IntStream
                .range(0, 10)
                .mapToObj(
                    i -> {
                        final String messageId = "" + currentValue + "-" + i;
                        final String messageContent = "Message, loop: " + currentValue + " id: " + i;
                        return SendMessageBatchRequestEntry.builder().id(messageId).messageBody(messageContent).build();
                    }
                )
                .collect(Collectors.toSet())
        );

        sqsAsyncClient.sendMessageBatch(batchRequestBuilder.build());
    }
}
