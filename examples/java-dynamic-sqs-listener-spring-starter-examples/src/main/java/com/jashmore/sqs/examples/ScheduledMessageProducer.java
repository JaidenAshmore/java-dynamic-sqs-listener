package com.jashmore.sqs.examples;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMessageProducer {
    private final AmazonSQSAsync amazonSqsAsync;

    /**
     * Scheduled job that sends messages to the queue for testing the listener.
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 1000)
    public void addMessage() {
        log.info("Sending messages to queues");
        final String testQueueUrl = amazonSqsAsync.getQueueUrl("test").getQueueUrl();
        amazonSqsAsync.sendMessage(testQueueUrl, "message");

        final String anotherTestQueueUrl = amazonSqsAsync.getQueueUrl("anotherTest").getQueueUrl();
        amazonSqsAsync.sendMessage(anotherTestQueueUrl, "message");
    }
}
