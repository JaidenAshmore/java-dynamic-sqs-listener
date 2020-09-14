package com.jashmore.sqs.examples;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.fifo.FifoListener;
import java.util.Map;
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
    private final Map<String, Integer> messageGroups = new ConcurrentHashMap<>();
    private final AtomicInteger totalMessagesProcessed = new AtomicInteger();
    private final Set<String> currentGroupsProcessing = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("unused")
    @FifoListener(value = "test-queue.fifo", concurrencyLevel = 10, batchSize = 2)
    public void batching(@Payload final String payload, final Message message) throws InterruptedException {
        int value = Integer.parseInt(payload);
        final String groupId = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
        synchronized (this) {
            if (currentGroupsProcessing.contains(groupId)) {
                log.error("Error! Already processing a message with the same group ID: {}", groupId);
            }
            final Integer previousValue = messageGroups.get(groupId);
            if ((previousValue == null && value != 1) || (previousValue != null && previousValue != value - 1)) {
                log.error(
                    "Error! Message with group ID is not processed in order: {}. Previous: {} Current: {}",
                    groupId,
                    previousValue,
                    value
                );
            }
            currentGroupsProcessing.add(groupId);
            messageGroups.put(groupId, value);
            //log.info("Current processed groups: {}", messageGroups);
            log.info(
                "Group: {} previous: {} value: {} concurrent: {} total {}",
                groupId,
                previousValue,
                value,
                messageGroups.size(),
                totalMessagesProcessed.incrementAndGet()
            );
        }
        Thread.sleep(2000);
        synchronized (this) {
            currentGroupsProcessing.remove(groupId);
        }
    }
}
