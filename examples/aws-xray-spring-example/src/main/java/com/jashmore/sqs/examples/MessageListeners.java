package com.jashmore.sqs.examples;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
@Component
@XRayEnabled
public class MessageListeners {
    @SuppressWarnings("unused")
    @QueueListener(identifier = "queue", value = "${sqs.queue.url}")
    public void sqsListener(final Message message) throws InterruptedException {
        log.info("Segment ID: {}", AWSXRay.getCurrentSegment().getTraceId());
        log.info("Message Received: {}", message.body());
        Thread.sleep(500);
    }
}
