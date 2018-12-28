package com.jashmore.sqs.examples;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.container.basic.QueueListener;
import com.jashmore.sqs.container.custom.CustomQueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
@SuppressWarnings("unused")
@Slf4j
public class MessageListeners {
    @QueueListener(value = "test")
    public void method(@Payload final String payload) {
        log.info("Message Received: {}", payload);
    }

    @CustomQueueListener(queue = "anotherTest",
            messageBrokerFactoryBeanName = "myMessageBrokerFactory",
            messageProcessorFactoryBeanName = "myMessageProcessorFactory",
            messageRetrieverFactoryBeanName = "myMessageRetrieverFactory"
    )
    public void configurableMethod(@Payload final String payload) {
        log.info("Configurable Message Received: {}", payload);
    }
}
