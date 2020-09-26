package com.jashmore.sqs.container.fifo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Min;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.processor.AsyncLambdaMessageProcessor;
import com.jashmore.sqs.processor.LambdaMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.util.CreateRandomQueueResponse;
import com.jashmore.sqs.util.ExpectedTestException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

@Slf4j
class FifoMessageListenerContainerIntegrationTest {
    static final ElasticMqSqsAsyncClient ELASTIC_MQ_SQS_ASYNC_CLIENT = new ElasticMqSqsAsyncClient();

    @AfterAll
    static void shutdown() {
        ELASTIC_MQ_SQS_ASYNC_CLIENT.close();
    }

    @Test
    void allMessagesInSameGroupAreProcessedInOrder() throws Exception {
        // arrange
        final int numberOfMessageGroups = 1;
        final int numberOfMessages = 50;
        final Map<String, List<String>> messageGroupProcessed = Collections.singletonMap("0", new ArrayList<>());
        final CountDownLatch messagesProcessedLatch = new CountDownLatch(numberOfMessages);
        final QueueProperties queueProperties = createFifoQueue();
        final MessageListenerContainer container = new FifoMessageListenerContainer(
            queueProperties,
            ELASTIC_MQ_SQS_ASYNC_CLIENT,
            () ->
                messageProcessor(
                    queueProperties,
                    message -> {
                        messageGroupProcessed
                            .get(message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID))
                            .add(message.body());
                        messagesProcessedLatch.countDown();
                    }
                ),
            ImmutableFifoMessageListenerContainerProperties
                .builder()
                .identifier("identifier")
                .concurrencyLevel(10)
                .maximumCachedMessageGroups(8)
                .maximumMessagesInMessageGroup(2)
                .build()
        );
        sendMessages(queueProperties, numberOfMessageGroups, numberOfMessages);

        // act
        container.start();
        messagesProcessedLatch.await(60, TimeUnit.SECONDS);
        container.stop();

        // assert
        assertThat(messageGroupProcessed).containsOnlyKeys("0");
        assertThat(messageGroupProcessed).containsEntry("0", listOfNumberStrings(numberOfMessages));
    }

    @Test
    void allMessagesInMessageGroupAreProcessedInOrder() throws Exception {
        // arrange
        final int numberOfMessageGroups = 5;
        final int numberOfMessages = 20;
        final Map<String, List<String>> messageGroupProcessed = IntStream
            .range(0, numberOfMessageGroups)
            .mapToObj(String::valueOf)
            .collect(Collectors.toMap(Function.identity(), i -> new ArrayList<>()));
        final CountDownLatch messagesProcessedLatch = new CountDownLatch(numberOfMessageGroups * numberOfMessages);
        final QueueProperties queueProperties = createFifoQueue();
        final MessageListenerContainer container = new FifoMessageListenerContainer(
            queueProperties,
            ELASTIC_MQ_SQS_ASYNC_CLIENT,
            () ->
                messageProcessor(
                    queueProperties,
                    message -> {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException interruptedException) {
                            throw new RuntimeException(interruptedException);
                        }
                        synchronized (messageGroupProcessed) {
                            messageGroupProcessed
                                .get(message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID))
                                .add(message.body());
                            messagesProcessedLatch.countDown();
                        }
                    }
                ),
            ImmutableFifoMessageListenerContainerProperties
                .builder()
                .identifier("identifier")
                .concurrencyLevel(10)
                .maximumCachedMessageGroups(7)
                .maximumMessagesInMessageGroup(2)
                .build()
        );
        sendMessages(queueProperties, numberOfMessageGroups, numberOfMessages);

        // act
        container.start();
        messagesProcessedLatch.await(30, TimeUnit.SECONDS);
        container.stop();

        // assert
        assertThat(messageGroupProcessed).containsOnlyKeys(listOfNumberStrings(numberOfMessageGroups));
        assertThat(messageGroupProcessed)
            .allSatisfy((key, list) -> assertThat(list).containsExactlyElementsOf(listOfNumberStrings(numberOfMessages)));
    }

    @Test
    void messagesInTheSameGroupAreNotProcessedConcurrently() throws Exception {
        // arrange
        final int numberOfMessageGroups = 5;
        final int numberOfMessages = 40;
        final CountDownLatch messagesProcessedLatch = new CountDownLatch(numberOfMessageGroups * numberOfMessages);
        final Set<String> currentMessages = new HashSet<>();
        final AtomicBoolean processedMessagesConcurrently = new AtomicBoolean();
        final QueueProperties queueProperties = createFifoQueue();
        final MessageListenerContainer container = new FifoMessageListenerContainer(
            queueProperties,
            ELASTIC_MQ_SQS_ASYNC_CLIENT,
            () ->
                messageProcessor(
                    queueProperties,
                    message -> {
                        final String messageGroupId = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
                        synchronized (currentMessages) {
                            if (currentMessages.contains(messageGroupId)) {
                                processedMessagesConcurrently.set(true);
                            }
                            currentMessages.add(messageGroupId);
                        }
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException interruptedException) {
                            // do nothing
                        }
                        synchronized (currentMessages) {
                            currentMessages.remove(messageGroupId);
                        }
                        messagesProcessedLatch.countDown();
                    }
                ),
            ImmutableFifoMessageListenerContainerProperties
                .builder()
                .identifier("identifier")
                .concurrencyLevel(10)
                .maximumCachedMessageGroups(8)
                .maximumMessagesInMessageGroup(2)
                .build()
        );
        sendMessages(queueProperties, numberOfMessageGroups, numberOfMessages);

        // act
        container.start();
        messagesProcessedLatch.await(60, TimeUnit.SECONDS);
        container.stop();

        // assert
        assertThat(processedMessagesConcurrently).isFalse();
    }

    @Test
    void failingMessagesShouldProcessMessagesInOrderUsingRedrivePolicy() throws Exception {
        // arrange
        final QueueProperties queueProperties = createFifoQueueWithDlq();
        final Set<String> failedMessageIds = ConcurrentHashMap.newKeySet();
        final Random random = new Random();
        final int numberOfMessageGroups = 10;
        final int numberOfMessages = 5;
        final CountDownLatch successfulMessages = new CountDownLatch(numberOfMessageGroups * numberOfMessages);
        final Map<String, List<String>> processedMessages = IntStream
            .range(0, numberOfMessageGroups)
            .mapToObj(i -> new AbstractMap.SimpleImmutableEntry<String, List<String>>("" + i, new ArrayList<>()))
            .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));
        sendMessages(queueProperties, numberOfMessageGroups, numberOfMessages);
        final MessageListenerContainer container = new FifoMessageListenerContainer(
            queueProperties,
            ELASTIC_MQ_SQS_ASYNC_CLIENT,
            () ->
                messageProcessor(
                    queueProperties,
                    message -> {
                        final String groupKey = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
                        synchronized (processedMessages) {
                            if (!failedMessageIds.contains(groupKey + "-" + message.body()) && random.nextInt(10) < 3) {
                                failedMessageIds.add(groupKey + "-" + message.body());
                                throw new ExpectedTestException();
                            }
                            processedMessages.get(groupKey).add(message.body());
                            successfulMessages.countDown();
                        }
                    }
                ),
            ImmutableFifoMessageListenerContainerProperties
                .builder()
                .identifier("error-test")
                .concurrencyLevel(10)
                .maximumCachedMessageGroups(8)
                .maximumMessagesInMessageGroup(2)
                .build()
        );

        // act
        container.start();
        successfulMessages.await(2, TimeUnit.MINUTES);
        container.stop();

        // assert
        assertThat(processedMessages).containsOnlyKeys(listOfNumberStrings(numberOfMessageGroups));
        assertThat(processedMessages)
            .allSatisfy(
                (groupId, messagesNumbers) -> assertThat(messagesNumbers).containsExactlyElementsOf(listOfNumberStrings(numberOfMessages))
            );
    }

    @Test
    void canProcessMessagesAsynchronously() throws Exception {
        // arrange
        final QueueProperties queueProperties = createFifoQueueWithDlq();
        final Set<String> failedMessageIds = ConcurrentHashMap.newKeySet();
        final int numberOfMessageGroups = 10;
        final int numberOfMessages = 5;
        final Random random = new Random();
        final CountDownLatch successfulMessagesLatch = new CountDownLatch(numberOfMessageGroups * numberOfMessages);
        final Map<String, List<String>> processedMessages = IntStream
            .range(0, numberOfMessageGroups)
            .mapToObj(String::valueOf)
            .collect(Collectors.toMap(Function.identity(), i -> new ArrayList<>()));
        sendMessages(queueProperties, numberOfMessageGroups, numberOfMessages);

        // act
        final MessageListenerContainer container = new FifoMessageListenerContainer(
            queueProperties,
            ELASTIC_MQ_SQS_ASYNC_CLIENT,
            () ->
                new AsyncLambdaMessageProcessor(
                    ELASTIC_MQ_SQS_ASYNC_CLIENT,
                    queueProperties,
                    message ->
                        CompletableFuture.runAsync(
                            () -> {
                                try {
                                    Thread.sleep(100);
                                } catch (final InterruptedException interruptedException) {
                                    return;
                                }

                                synchronized (processedMessages) {
                                    final String groupKey = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
                                    if (!failedMessageIds.contains(groupKey + "-" + message.body()) && random.nextInt(10) < 2) {
                                        failedMessageIds.add(groupKey + "-" + message.body());
                                        throw new ExpectedTestException();
                                    }
                                    processedMessages.get(groupKey).add(message.body());
                                    successfulMessagesLatch.countDown();
                                }
                            }
                        )
                ),
            ImmutableFifoMessageListenerContainerProperties
                .builder()
                .identifier("async-error-test")
                .concurrencyLevel(10)
                .maximumCachedMessageGroups(10)
                .maximumMessagesInMessageGroup(2)
                .build()
        );
        container.start();
        successfulMessagesLatch.await(2, TimeUnit.MINUTES);
        container.stop();

        // assert
        assertThat(processedMessages).containsOnlyKeys(listOfNumberStrings(numberOfMessageGroups));
        assertThat(processedMessages)
            .allSatisfy(
                (groupId, messagesNumbers) -> assertThat(messagesNumbers).containsExactlyElementsOf(listOfNumberStrings(numberOfMessages))
            );
    }

    private static MessageProcessor messageProcessor(final QueueProperties queueProperties, final Consumer<Message> lambda) {
        return new LambdaMessageProcessor(ELASTIC_MQ_SQS_ASYNC_CLIENT, queueProperties, lambda);
    }

    private static List<String> listOfNumberStrings(final int endExclusive) {
        return IntStream.range(0, endExclusive).mapToObj(String::valueOf).collect(Collectors.toList());
    }

    private static void sendMessages(
        final QueueProperties queueProperties,
        @Max(10) final int numberOfGroups,
        @Min(1) final int numberOfMessages
    )
        throws Exception {
        for (int i = 0; i < numberOfMessages; ++i) {
            final int messageIndex = i;
            ELASTIC_MQ_SQS_ASYNC_CLIENT
                .sendMessageBatch(
                    sendMessageBuilder -> {
                        final List<SendMessageBatchRequestEntry> entries = IntStream
                            .range(0, numberOfGroups)
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
                        sendMessageBuilder.queueUrl(queueProperties.getQueueUrl()).entries(entries);
                    }
                )
                .get(5, TimeUnit.SECONDS);
        }
    }

    private QueueProperties createFifoQueueWithDlq() throws Exception {
        final CreateRandomQueueResponse deadLetterQueueResponse = ELASTIC_MQ_SQS_ASYNC_CLIENT.createRandomFifoQueue().get();
        final GetQueueAttributesResponse attributes = ELASTIC_MQ_SQS_ASYNC_CLIENT
            .getQueueAttributes(
                builder -> builder.queueUrl(deadLetterQueueResponse.queueUrl()).attributeNames(QueueAttributeName.QUEUE_ARN)
            )
            .get();

        final String queueUrl = ELASTIC_MQ_SQS_ASYNC_CLIENT
            .createRandomFifoQueue(
                builder -> {
                    final Map<QueueAttributeName, String> queueAttributes = new HashMap<>();
                    queueAttributes.put(
                        QueueAttributeName.REDRIVE_POLICY,
                        String.format(
                            "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"3\"}",
                            attributes.attributes().get(QueueAttributeName.QUEUE_ARN)
                        )
                    );
                    queueAttributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, "10");
                    builder.attributes(queueAttributes);
                }
            )
            .get()
            .queueUrl();

        return QueueProperties.builder().queueUrl(queueUrl).build();
    }

    private QueueProperties createFifoQueue() throws Exception {
        final String queueUrl = ELASTIC_MQ_SQS_ASYNC_CLIENT.createRandomFifoQueue().get().queueUrl();

        return QueueProperties.builder().queueUrl(queueUrl).build();
    }
}
