package com.jashmore.sqs.examples;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMessageProducer {
    private final AmazonSQSAsync amazonSqsAsync;
    private final AtomicInteger count = new AtomicInteger();

    /**
     * Scheduled job that sends messages to the queue for testing the listener.
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 1000)
    public void addMessages() {
        log.info("Putting 10 messages onto each queue");
        final int currentValue = count.incrementAndGet();

        sendMessagesToQueue("test", currentValue);
        sendMessagesToQueue("anotherTest", currentValue);
    }

    private void sendMessagesToQueue(final String queueName,
                                     final int currentValue) {
        final String queueUrl = amazonSqsAsync.getQueueUrl(queueName).getQueueUrl();

        final SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest(queueUrl);
        for (int i = 0; i < 10; ++i) {
            final String messageId = "" + currentValue + "-" + i;
            final String messeageContent = "Message, loop: " + currentValue + " id: " + i;
            sendMessageBatchRequest.withEntries(new SendMessageBatchRequestEntry(messageId, messeageContent));
        }

        amazonSqsAsync.sendMessageBatch(sendMessageBatchRequest);
    }
}
