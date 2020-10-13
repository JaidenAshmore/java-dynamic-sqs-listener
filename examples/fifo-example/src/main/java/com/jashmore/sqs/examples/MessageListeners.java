package com.jashmore.sqs.examples;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.fifo.FifoQueueListener;
import com.jashmore.sqs.util.ExpectedTestException;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@Slf4j
@Component
public class MessageListeners {

    private static final int MINIMUM_PROCESSING_TIME_IN_MS = 500;
    private static final int MAXIMUM_PROCESSING_TIME_IN_MS = 2000;
    private final Map<String, Integer> messageGroups = new ConcurrentHashMap<>();
    private final AtomicInteger totalMessagesProcessed = new AtomicInteger();
    private final Set<String> currentGroupsProcessing = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    @FifoQueueListener(
        identifier = "fifo",
        value = "test-queue.fifo",
        concurrencyLevel = 10,
        messageVisibilityTimeoutInSeconds = 60,
        tryAndProcessAnyExtraRetrievedMessagesOnShutdown = true
    )
    public void fifoQueueListener(@Payload final String payload, final Message message) throws InterruptedException {
        final int value = Integer.parseInt(payload);
        final int processingTimeInMs =
            MINIMUM_PROCESSING_TIME_IN_MS + random.nextInt(MAXIMUM_PROCESSING_TIME_IN_MS - MINIMUM_PROCESSING_TIME_IN_MS);
        final String groupId = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
        synchronized (this) {
            if (currentGroupsProcessing.contains(groupId)) {
                throw new RuntimeException("Error! Already processing a message with the same group ID: " + groupId);
            }
            final Integer previousValue = messageGroups.get(groupId);
            if ((previousValue == null && value != 1) || (previousValue != null && previousValue != value - 1)) {
                log.error(
                    "Error! Message with group ID is not processed in order: {}. Previous: {} Current: {}",
                    groupId,
                    previousValue,
                    value
                );
                throw new RuntimeException("Error! Incorrect order for group: " + groupId);
            }

            if (random.nextInt(10) < 1) {
                throw new ExpectedTestException();
            }

            currentGroupsProcessing.add(groupId);
            messageGroups.put(groupId, value);
            log.info(
                "Group: {} previous: {} value: {} concurrent: {} total {} in {}ms",
                groupId,
                previousValue,
                value,
                currentGroupsProcessing.size(),
                totalMessagesProcessed.incrementAndGet(),
                processingTimeInMs
            );
        }
        Thread.sleep(processingTimeInMs);
        synchronized (this) {
            currentGroupsProcessing.remove(groupId);
        }
    }
}
