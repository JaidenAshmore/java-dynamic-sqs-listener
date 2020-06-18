package com.jashmore.sqs.examples;

import com.jashmore.sqs.util.LocalSqsAsyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Helper scheduled task that will place 10 messages onto each queue for processing by the message listeners.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMessageProducer {
    private final LocalSqsAsyncClient sqsAsyncClient;
    private final AtomicInteger count = new AtomicInteger();

    @Scheduled(initialDelay = 1000, fixedDelay = 1000)
    public void addMessages() {
        final int currentValue = count.incrementAndGet();
        sqsAsyncClient.sendMessage("queue-name", "payload-" + currentValue);
    }
}
