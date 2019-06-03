package com.jashmore.sqs.examples;

import static com.jashmore.sqs.examples.ExampleConstants.MESSAGE_IO_TIME_IN_MS;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingQueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Each of the types of messages listeners that are available.
 *
 * <p>To test a different implementation, uncomment it and comment out the current one as you should only have one running at a time.
 */
@Component
@SuppressWarnings("unused")
@Slf4j
public class MessageListeners {
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicLong firstTime = new AtomicLong(0);

    //@PrefetchingQueueListener(value = "queueName", concurrencyLevel = 10, desiredMinPrefetchedMessages = 50, maxPrefetchedMessages = 60)
    //public void prefetchingConcurrency10(@Payload final String payload) throws Exception {
    //    handleMethod();
    //}

    @PrefetchingQueueListener(value = "queueName", concurrencyLevel = 30, desiredMinPrefetchedMessages = 50, maxPrefetchedMessages = 60)
    public void prefetchingConcurrency30(@Payload final String payload) throws Exception {
        handleMethod();
    }

    //@QueueListener(value = "queueName", concurrencyLevel = 10)
    //public void queueListenerMethodConcurrency10(@Payload final String payload) throws Exception {
    //    handleMethod();
    //}

    //@QueueListener(value = "queueName", concurrencyLevel = 30)
    //public void queueListenerMethodConcurrency30(@Payload final String payload) throws Exception {
    //    handleMethod();
    //}
    //
    //@SqsListener("queueName")
    //public void springCloudConcurrency10(final String payload) throws Exception {
    //    handleMethod();
    //}
    //
    //@JmsListener(destination = "queueName", concurrency = "10")
    //public void jmsConcurrency10(String message) throws Exception {
    //    handleMethod();
    //}
    //
    //@JmsListener(destination = "queueName", concurrency = "30")
    //public void jmsConcurrency30(String message) throws Exception {
    //    handleMethod();
    //}

    private void handleMethod() throws Exception {
        firstTime.compareAndSet(0, System.currentTimeMillis());
        final int currentCount = count.incrementAndGet();
        if (currentCount % 100 == 0) {
            log.info("Time for processing {} messages is {}ms", currentCount, System.currentTimeMillis() - firstTime.get());
        }

        Thread.sleep(MESSAGE_IO_TIME_IN_MS);
    }
}
