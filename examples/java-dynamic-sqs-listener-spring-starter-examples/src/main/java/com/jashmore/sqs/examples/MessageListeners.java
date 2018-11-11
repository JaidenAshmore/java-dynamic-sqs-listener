package com.jashmore.sqs.examples;

import com.jashmore.sqs.annotation.QueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MessageListeners {
    private static final Logger log = LoggerFactory.getLogger(MessageListeners.class);

    @QueueListener("test")
    public void method(@Payload String payload) {
        log.info("Message Received: {}", payload);
    }
}
