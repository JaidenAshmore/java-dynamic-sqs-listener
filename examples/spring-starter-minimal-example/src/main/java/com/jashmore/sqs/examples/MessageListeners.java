package com.jashmore.sqs.examples;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unused")
public class MessageListeners {
    private static final Logger log = LoggerFactory.getLogger(MessageListeners.class);

    @QueueListener(value = "myQueueName")
    public void listener(@Payload final String payload) {
        log.info("Message received: {}", payload);
    }
}
