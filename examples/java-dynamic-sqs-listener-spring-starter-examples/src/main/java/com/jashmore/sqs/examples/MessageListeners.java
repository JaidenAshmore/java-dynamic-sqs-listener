package com.jashmore.sqs.examples;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.container.basic.QueueListener;
import com.jashmore.sqs.container.custom.CustomQueueListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MessageListeners {
    private static final Logger log = LoggerFactory.getLogger(MessageListeners.class);

    @QueueListener(value = "test", concurrencyLevel = 2)
    public void method(@Payload String payload) {
        log.info("Message Received: {}", payload);
    }

    @CustomQueueListener(queue = "anotherTest",
            messageBrokerFactoryBeanName = "myMessageBrokerFactory",
            messageProcessorFactoryBeanName = "myMessageProcessorFactory",
            messageRetrieverFactoryBeanName = "myMessageRetrieverFactory"
    )
    public void configurableMethod(@Payload String payload) {
        log.info("Configurable Message Received: {}", payload);
    }
}
