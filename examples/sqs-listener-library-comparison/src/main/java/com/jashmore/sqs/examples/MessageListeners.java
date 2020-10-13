package com.jashmore.sqs.examples;

import static com.jashmore.sqs.examples.ExampleConstants.MESSAGE_IO_TIME_IN_MS;
import static com.jashmore.sqs.examples.Queues.JMS_10_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.JMS_30_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.PREFETCHING_10_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.PREFETCHING_30_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.QUEUE_LISTENER_10_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.QUEUE_LISTENER_30_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.SPRING_CLOUD_QUEUE_NAME;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT;

import com.jashmore.sqs.argument.attribute.MessageSystemAttribute;
import com.jashmore.sqs.argument.messageid.MessageId;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingQueueListener;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.support.JmsHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Each of the types of messages listeners that are available.
 *
 * <p>If you want a true performance test you should remove all of the other listener implementations so that they don't impact each other.
 */
@Component
@SuppressWarnings("unused")
@Slf4j
public class MessageListeners {

    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicLong firstTime = new AtomicLong(0);

    @PrefetchingQueueListener(
        value = PREFETCHING_10_QUEUE_NAME,
        concurrencyLevel = 10,
        desiredMinPrefetchedMessages = 50,
        maxPrefetchedMessages = 60
    )
    public void prefetchingConcurrency10(
        @Payload final String payload,
        @MessageId final String messageId,
        @MessageSystemAttribute(APPROXIMATE_RECEIVE_COUNT) int receiveCount
    )
        throws Exception {
        handleMethod();
    }

    @PrefetchingQueueListener(
        value = PREFETCHING_30_QUEUE_NAME,
        concurrencyLevel = 30,
        desiredMinPrefetchedMessages = 50,
        maxPrefetchedMessages = 60
    )
    public void prefetchingConcurrency30(
        @Payload final String payload,
        @MessageId final String messageId,
        @MessageSystemAttribute(APPROXIMATE_RECEIVE_COUNT) int receiveCount
    )
        throws Exception {
        handleMethod();
    }

    @QueueListener(value = QUEUE_LISTENER_10_QUEUE_NAME, concurrencyLevel = 10)
    public void queueListenerMethodConcurrency10(
        @Payload final String payload,
        @MessageId final String messageId,
        @MessageSystemAttribute(APPROXIMATE_RECEIVE_COUNT) int receiveCount
    )
        throws Exception {
        handleMethod();
    }

    @QueueListener(value = QUEUE_LISTENER_30_QUEUE_NAME, concurrencyLevel = 30)
    public void queueListenerMethodConcurrency30(
        @Payload final String payload,
        @MessageId final String messageId,
        @MessageSystemAttribute(APPROXIMATE_RECEIVE_COUNT) int receiveCount
    )
        throws Exception {
        handleMethod();
    }

    @SqsListener(SPRING_CLOUD_QUEUE_NAME)
    public void springCloudConcurrency10(
        final String payload,
        @Header("ApproximateReceiveCount") String receiveCount,
        @Header("MessageId") String messageId
    )
        throws Exception {
        handleMethod();
    }

    @JmsListener(destination = JMS_10_QUEUE_NAME, concurrency = "10")
    public void jmsConcurrency10(
        String message,
        @Header("JMSXDeliveryCount") String receiveCount,
        @Header(JmsHeaders.MESSAGE_ID) String messageId
    )
        throws Exception {
        handleMethod();
    }

    @JmsListener(destination = JMS_30_QUEUE_NAME, concurrency = "30")
    public void jmsConcurrency30(
        String message,
        @Header("JMSXDeliveryCount") String receiveCount,
        @Header(JmsHeaders.MESSAGE_ID) String messageId
    )
        throws Exception {
        handleMethod();
    }

    private void handleMethod() throws Exception {
        firstTime.compareAndSet(0, System.currentTimeMillis());
        Thread.sleep(MESSAGE_IO_TIME_IN_MS);
        final int currentCount = count.incrementAndGet();
        if (currentCount % 100 == 0) {
            log.info("Time for processing {} messages is {}ms", currentCount, System.currentTimeMillis() - firstTime.get());
        }
    }
}
