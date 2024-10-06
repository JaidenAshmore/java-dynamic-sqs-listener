package com.jashmore.sqs.examples;

import static com.jashmore.sqs.examples.Application.QUEUE_NAME;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.annotations.decorator.visibilityextender.AutoVisibilityExtender;
import com.jashmore.sqs.argument.attribute.MessageSystemAttribute;
import com.jashmore.sqs.argument.messageid.MessageId;
import com.jashmore.sqs.processor.argument.Acknowledge;
import java.time.Duration;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@Slf4j
@Component
public class MessageListeners {

    private static final int MAXIMUM_DURATION_IN_SECONDS = 16;
    private static final Set<String> interruptedMessages = ConcurrentHashMap.newKeySet();
    private static final Random random = new Random();

    @QueueListener(
        identifier = "queue",
        value = QUEUE_NAME,
        concurrencyLevel = 10,
        messageVisibilityTimeoutInSeconds = 5,
        interruptThreadsProcessingMessagesOnShutdown = true
    )
    @AutoVisibilityExtender(visibilityTimeoutInSeconds = 5, maximumDurationInSeconds = MAXIMUM_DURATION_IN_SECONDS, bufferTimeInSeconds = 1)
    public void longRunningTaskListener(
        @MessageId final String messageId,
        @MessageSystemAttribute(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT) final String approximateReceiveCount,
        final Acknowledge acknowledge
    ) {
        if (!approximateReceiveCount.equals("1") && !interruptedMessages.contains(messageId)) {
            log.error("Error! Received message {} that should not be retried", messageId);
        }
        final long startTime = System.currentTimeMillis();
        final int maxWait = MAXIMUM_DURATION_IN_SECONDS + 5;
        final int length = random.nextInt(maxWait);
        log.info("Message {} will take {} seconds to process", messageId, length);

        try {
            boolean wasInterrupted = false;
            try {
                Thread.sleep(Duration.ofSeconds(length).toMillis());
            } catch (InterruptedException e) {
                wasInterrupted = true;
            }

            final long processingTime = System.currentTimeMillis() - startTime;
            log.info("Message {} took {}ms to process", messageId, processingTime);
            if (processingTime < (MAXIMUM_DURATION_IN_SECONDS - 1) * 1000) {
                if (wasInterrupted) {
                    log.error("Message {} should not have been interrupted", messageId);
                }
            } else if (processingTime > (MAXIMUM_DURATION_IN_SECONDS + 1) * 1000) {
                if (!wasInterrupted) {
                    log.error("Message {} should have been interrupted", messageId);
                }
            } else {
                log.info("Message {} is too close to interruption time to show error", messageId);
            }

            if (wasInterrupted) {
                interruptedMessages.add(messageId);
            } else {
                try {
                    acknowledge.acknowledgeSuccessful().get();
                } catch (InterruptedException e) {
                    // do nothing
                } catch (Exception e) {
                    log.error("Error acknowledging message" + messageId, e);
                }
            }
        } finally {
            log.info("Message {} finished", messageId);
        }
    }
}
