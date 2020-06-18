package com.jashmore.sqs.examples;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@XRayEnabled
public class MessageListeners {

    @SuppressWarnings("unused")
    @QueueListener(value = "queue-name")
    public void batching(@Payload final String payload) throws InterruptedException {
        log.info("Batching Message Received: {}", payload);
        Thread.sleep(500);
    }
}
