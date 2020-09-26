package com.jashmore.sqs.spring.container.fifo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.attribute.MessageSystemAttribute;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

@Slf4j
@SpringBootTest(classes = { FifoMessageListenerContainerFactoryTest.TestConfig.class, QueueListenerConfiguration.class })
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = { "prop.concurrency=5" })
public class FifoMessageListenerContainerFactoryTest {
    private static final String QUEUE_NAME = "FifoMessageListenerContainerFactoryTest.fifo";
    private static final int NUMBER_OF_MESSAGE_GROUPS = 5;
    private static final int NUMBER_OF_MESSAGES_TO_SEND = 20;
    private static final CountDownLatch MESSAGE_PROCESSED_LATCH = new CountDownLatch(NUMBER_OF_MESSAGE_GROUPS * NUMBER_OF_MESSAGES_TO_SEND);
    private static final Map<String, List<String>> MESSAGE_GROUPS_PROCESSED = IntStream
        .range(0, NUMBER_OF_MESSAGE_GROUPS)
        .mapToObj(String::valueOf)
        .collect(Collectors.toMap(Function.identity(), i -> new ArrayList<>()));

    private static final int MESSAGE_VISIBILITY_IN_SECONDS = 10;

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_NAME).fifoQueue(true).build());
        }

        @Service
        public static class MessageListener {

            @FifoQueueListener(
                value = QUEUE_NAME,
                messageVisibilityTimeoutInSeconds = MESSAGE_VISIBILITY_IN_SECONDS,
                concurrencyLevelString = "${prop.concurrency}"
            )
            public void listenToMessage(
                @MessageSystemAttribute(MessageSystemAttributeName.MESSAGE_GROUP_ID) final String groupId,
                @Payload final String body
            ) {
                log.info("Received message {} for group: {}", body, groupId);
                MESSAGE_GROUPS_PROCESSED.get(groupId).add(body);
                MESSAGE_PROCESSED_LATCH.countDown();
            }
        }
    }

    @Test
    void allMessagesAreProcessedByListeners() throws Exception {
        // arrange
        final String queueUrl = localSqsAsyncClient
            .getQueueUrl(builder -> builder.queueName(QUEUE_NAME))
            .get(5, TimeUnit.SECONDS)
            .queueUrl();
        for (int i = 0; i < NUMBER_OF_MESSAGES_TO_SEND; ++i) {
            final int messageIndex = i;
            localSqsAsyncClient
                .sendMessageBatch(
                    sendMessageBuilder -> {
                        final List<SendMessageBatchRequestEntry> entries = IntStream
                            .range(0, NUMBER_OF_MESSAGE_GROUPS)
                            .mapToObj(
                                groupIndex -> {
                                    final String messageId = "" + messageIndex + "-" + groupIndex;
                                    return SendMessageBatchRequestEntry
                                        .builder()
                                        .id(messageId)
                                        .messageGroupId(String.valueOf(groupIndex))
                                        .messageBody("" + messageIndex)
                                        .messageDeduplicationId(messageId)
                                        .build();
                                }
                            )
                            .collect(Collectors.toList());
                        sendMessageBuilder.queueUrl(queueUrl).entries(entries);
                    }
                )
                .get(5, TimeUnit.SECONDS);
        }

        // act
        MESSAGE_PROCESSED_LATCH.await(2, TimeUnit.MINUTES);

        // assert
        assertThat(MESSAGE_GROUPS_PROCESSED).containsOnlyKeys(listOfNumberStrings(NUMBER_OF_MESSAGE_GROUPS));
        assertThat(MESSAGE_GROUPS_PROCESSED)
            .allSatisfy(
                (groupId, messagesNumbers) ->
                    assertThat(messagesNumbers).containsExactlyElementsOf(listOfNumberStrings(NUMBER_OF_MESSAGES_TO_SEND))
            );
    }

    private static List<String> listOfNumberStrings(final int endExclusive) {
        return IntStream.range(0, endExclusive).mapToObj(String::valueOf).collect(Collectors.toList());
    }
}
