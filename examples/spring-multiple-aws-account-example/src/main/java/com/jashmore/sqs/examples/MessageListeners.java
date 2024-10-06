package com.jashmore.sqs.examples;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.argument.payload.Payload;
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
    @QueueListener(value = "firstClientQueue", sqsClient = "firstClient")
    public void firstClientMessageProcessing(@Payload final String payload) {
        log.info("Message Received from firstClient#firstClientQueue: {}", payload);
    }

    /**
     * Basic queue listener.
     *
     * @param payload the payload of the SQS Message
     */
    @QueueListener(value = "secondClientQueue", sqsClient = "secondClient")
    public void secondClientMessageProcessing(@Payload final String payload) {
        log.info("Message Received from secondClient#secondClientQueue: {}", payload);
    }
}
