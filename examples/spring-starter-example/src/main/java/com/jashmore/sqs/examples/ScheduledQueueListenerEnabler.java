package com.jashmore.sqs.examples;

import com.jashmore.sqs.container.MessageListenerContainerCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledQueueListenerEnabler {

    private final MessageListenerContainerCoordinator messageListenerContainerCoordinator;

    /**
     * Just a scheduled job that shows that there are methods of turning off the container when necessary.
     *
     * @throws InterruptedException if the thread was interrupted while sleeping
     */
    @Scheduled(initialDelay = 10_000, fixedDelay = 30_000)
    public void turnOfSqsListener() throws InterruptedException {
        log.info("Turning off SQS Listener for a short period");

        messageListenerContainerCoordinator.stopAllContainers();
        Thread.sleep(5_000);
        log.info("Turning SQS Listener back on");
        messageListenerContainerCoordinator.startAllContainers();
    }
}
