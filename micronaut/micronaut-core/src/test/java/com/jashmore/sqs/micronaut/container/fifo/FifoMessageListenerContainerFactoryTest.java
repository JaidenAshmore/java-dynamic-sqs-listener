package com.jashmore.sqs.micronaut.container.fifo;

import com.jashmore.sqs.util.LocalSqsAsyncClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "prop.concurrency", value = "5")
@Property(name = "FifoMessageListenerContainerFactoryTest", value = "true")
public class FifoMessageListenerContainerFactoryTest {

    static final String QUEUE_NAME = "FifoMessageListenerContainerFactoryTest.fifo";
    static final int NUMBER_OF_MESSAGE_GROUPS = 5;
    static final int NUMBER_OF_MESSAGES_TO_SEND = 20;
    static final CountDownLatch MESSAGE_PROCESSED_LATCH = new CountDownLatch(NUMBER_OF_MESSAGE_GROUPS * NUMBER_OF_MESSAGES_TO_SEND);
    static final Map<String, List<String>> MESSAGE_GROUPS_PROCESSED = IntStream
        .range(0, NUMBER_OF_MESSAGE_GROUPS)
        .mapToObj(String::valueOf)
        .collect(Collectors.toMap(Function.identity(), i -> new ArrayList<>()));

    static final int MESSAGE_VISIBILITY_IN_SECONDS = 10;

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

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
                .sendMessageBatch(sendMessageBuilder -> {
                    final List<SendMessageBatchRequestEntry> entries = IntStream
                        .range(0, NUMBER_OF_MESSAGE_GROUPS)
                        .mapToObj(groupIndex -> {
                            final String messageId = "" + messageIndex + "-" + groupIndex;
                            return SendMessageBatchRequestEntry
                                .builder()
                                .id(messageId)
                                .messageGroupId(String.valueOf(groupIndex))
                                .messageBody("" + messageIndex)
                                .messageDeduplicationId(messageId)
                                .build();
                        })
                        .collect(Collectors.toList());
                    sendMessageBuilder.queueUrl(queueUrl).entries(entries);
                })
                .get(5, TimeUnit.SECONDS);
        }

        // act
        MESSAGE_PROCESSED_LATCH.await(2, TimeUnit.MINUTES);

        // assert
        assertThat(MESSAGE_GROUPS_PROCESSED).containsOnlyKeys(listOfNumberStrings(NUMBER_OF_MESSAGE_GROUPS));
        assertThat(MESSAGE_GROUPS_PROCESSED)
            .allSatisfy((groupId, messagesNumbers) ->
                assertThat(messagesNumbers).containsExactlyElementsOf(listOfNumberStrings(NUMBER_OF_MESSAGES_TO_SEND))
            );
    }

    private static List<String> listOfNumberStrings(final int endExclusive) {
        return IntStream.range(0, endExclusive).mapToObj(String::valueOf).collect(Collectors.toList());
    }
}
