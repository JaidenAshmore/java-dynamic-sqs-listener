package com.jashmore.sqs.micronaut.container.fifo;

import com.jashmore.sqs.argument.attribute.MessageSystemAttribute;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@Slf4j
@Factory
@Requires(property = "FifoMessageListenerContainerFactoryTest")
public class TestFactory {

    @Singleton
    public LocalSqsAsyncClient localSqsAsyncClient() {
        return new ElasticMqSqsAsyncClient(SqsQueuesConfig.QueueConfig.builder().queueName(FifoMessageListenerContainerFactoryTest.QUEUE_NAME).fifoQueue(true).build());
    }

    @Singleton
    public static class MessageListener {

        @FifoQueueListener(
                value = FifoMessageListenerContainerFactoryTest.QUEUE_NAME,
                messageVisibilityTimeoutInSeconds = FifoMessageListenerContainerFactoryTest.MESSAGE_VISIBILITY_IN_SECONDS,
                concurrencyLevelString = "${prop.concurrency}"
        )
        public void listenToMessage(
                @MessageSystemAttribute(MessageSystemAttributeName.MESSAGE_GROUP_ID) final String groupId,
                @Payload final String body
        ) {
            log.info("Received message {} for group: {}", body, groupId);
            FifoMessageListenerContainerFactoryTest.MESSAGE_GROUPS_PROCESSED.get(groupId).add(body);
            FifoMessageListenerContainerFactoryTest.MESSAGE_PROCESSED_LATCH.countDown();
        }
    }
}
