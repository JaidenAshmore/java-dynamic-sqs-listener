package com.jashmore.sqs.examples;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unused")
@Slf4j
public class MessageListeners {
    /**
     * Basic queue listener.
     *
     * @param payload the payload of the SQS Message
     */
    @QueueListener(value = "test", identifier = "test")
    public void method(@Payload final String payload) {
        log.info("Message Received: {}", payload);
    }
}
