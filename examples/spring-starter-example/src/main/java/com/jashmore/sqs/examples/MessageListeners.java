package com.jashmore.sqs.examples;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.annotations.core.prefetch.PrefetchingQueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unused")
@Slf4j
public class MessageListeners {

    private final AtomicInteger numberMessagesConcurrentlyBeingProcessed = new AtomicInteger(0);
    private final AtomicInteger numberMessagesConcurrentlyBeingProcessedBatching = new AtomicInteger(0);

    /**
     * Basic queue listener.
     *
     * @param payload the payload of the SQS Message
     * @throws InterruptedException if the thread was interrupted while sleeping
     */
    @QueueListener(value = "anotherTest", concurrencyLevel = 4, batchSize = 4)
    public void batching(@Payload final String payload) throws InterruptedException {
        try {
            log.info("{} Batching Message Received: {}", numberMessagesConcurrentlyBeingProcessedBatching.incrementAndGet(), payload);
            Thread.sleep(500);
        } finally {
            numberMessagesConcurrentlyBeingProcessedBatching.decrementAndGet();
        }
    }

    /**
     * Basic queue listener.
     *
     * @param payload the payload of the SQS Message
     * @throws InterruptedException if the thread was interrupted while sleeping
     */
    @PrefetchingQueueListener(value = "test", concurrencyLevel = 4, desiredMinPrefetchedMessages = 40, maxPrefetchedMessages = 50)
    public void prefetching(@Payload final String payload) throws InterruptedException {
        try {
            log.info("{} Prefetching Message Received: {}", numberMessagesConcurrentlyBeingProcessed.incrementAndGet(), payload);
            Thread.sleep(500);
        } finally {
            numberMessagesConcurrentlyBeingProcessed.decrementAndGet();
        }
    }
}
