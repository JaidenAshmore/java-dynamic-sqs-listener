package com.jashmore.sqs.examples;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Entity;
import com.amazonaws.xray.entities.Segment;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
@Component
public class MessageListeners {
    private final SomeService someService;

    @Autowired
    public MessageListeners(final SomeService someService) {
        this.someService = someService;
    }

    @SuppressWarnings("unused")
    @QueueListener(identifier = "queue", value = "${sqs.queue.url}")
    public CompletableFuture<Void> sqsListener(final Message message) {
        final Entity currentTraceEntity = AWSXRay.getTraceEntity();
        final Segment currentSegment = AWSXRay.getCurrentSegment();
        if (currentSegment == null) {
            throw new RuntimeException("Expected segment");
        }
        log.info("Segment ID: {}", currentSegment.getTraceId());
        return CompletableFuture.runAsync(
            () -> {
                AWSXRay.setTraceEntity(currentTraceEntity);
                try {
                    someService.someMethod();
                } catch (final InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(interruptedException);
                } finally {
                    log.info("Done");
                    AWSXRay.clearTraceEntity();
                }
            }
        );
    }
}
