package com.jashmore.sqs.broker.grouping;

import static com.jashmore.sqs.broker.util.MessageBrokerTestUtils.runBrokerProcessMessageOnThread;
import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@Slf4j
@ExtendWith(MockitoExtension.class)
class GroupingMessageBrokerTest {
    static final String GROUP_A = "groupA";
    static final String GROUP_B = "groupB";
    static final String GROUP_C = "groupC";
    private static final ImmutableGroupingMessageBrokerProperties DEFAULT_PROPERTIES = ImmutableGroupingMessageBrokerProperties
        .builder()
        .concurrencyLevel(2)
        .messageGroupingFunction(message -> message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID))
        .maximumNumberOfCachedMessageGroups(2)
        .build();

    private ExecutorService brokerExecutorService;
    private ExecutorService messageProcessorExecutorService;

    private final AtomicInteger messageId = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        brokerExecutorService = Executors.newCachedThreadPool();
        messageProcessorExecutorService = Executors.newCachedThreadPool();

        messageId.set(0);
    }

    @AfterEach
    void tearDown() {
        brokerExecutorService.shutdownNow();
        messageProcessorExecutorService.shutdownNow();
    }

    @Test
    void shouldNotProcessTwoMessagesInSameGroupConcurrently() throws Exception {
        // arrange
        final GroupingMessageBroker broker = new GroupingMessageBroker(DEFAULT_PROPERTIES);
        final Message firstMessage = createMessage(GROUP_A);
        final Message secondMessage = createMessage(GROUP_A);
        final Message thirdMessage = createMessage(GROUP_B);
        final CountDownLatch messageProcessingLatch = new CountDownLatch(2);
        final Set<Message> processingMessages = ConcurrentHashMap.newKeySet();

        // act
        runBrokerProcessMessageOnThread(
            broker,
            buildMessageSupplier(firstMessage, secondMessage, thirdMessage),
            message ->
                CompletableFuture.runAsync(
                    () -> {
                        log.info("Processing message: {}", message.messageId());
                        processingMessages.add(message);
                        messageProcessingLatch.countDown();
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (final InterruptedException interruptedException) {
                            //expected
                        }
                        log.info("Done");
                    },
                    messageProcessorExecutorService
                ),
            brokerExecutorService
        );

        // assert
        assertThat(messageProcessingLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(processingMessages).containsExactlyInAnyOrder(firstMessage, thirdMessage);
    }

    @Test
    void willProcessAsManyMessagesConcurrentlyAsPossible() throws Exception {
        // arrange
        final GroupingMessageBroker broker = new GroupingMessageBroker(
            ImmutableGroupingMessageBrokerProperties.builder().from(DEFAULT_PROPERTIES).concurrencyLevel(3).build()
        );
        final Message firstMessage = createMessage(GROUP_A);
        final Message secondMessage = createMessage(GROUP_B);
        final Message thirdMessage = createMessage(GROUP_C);
        final CountDownLatch messageProcessingLatch = new CountDownLatch(3);
        final Set<Message> processingMessages = ConcurrentHashMap.newKeySet();

        // act
        runBrokerProcessMessageOnThread(
            broker,
            buildMessageSupplier(firstMessage, secondMessage, thirdMessage),
            message ->
                CompletableFuture.runAsync(
                    () -> {
                        log.info("Processing message: {}", message.messageId());
                        processingMessages.add(message);
                        messageProcessingLatch.countDown();
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (final InterruptedException interruptedException) {
                            //expected
                        }
                        log.info("Done");
                    },
                    messageProcessorExecutorService
                ),
            brokerExecutorService
        );

        // assert
        assertThat(messageProcessingLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(processingMessages).containsExactlyInAnyOrder(firstMessage, secondMessage, thirdMessage);
    }

    @Test
    void willObtainMoreMessagesWhenMessagesCompleted() throws Exception {
        // arrange
        final GroupingMessageBroker broker = new GroupingMessageBroker(
            ImmutableGroupingMessageBrokerProperties.builder().from(DEFAULT_PROPERTIES).concurrencyLevel(1).build()
        );
        final Message firstMessage = createMessage(GROUP_A);
        final Message secondMessage = createMessage(GROUP_B);
        final Message thirdMessage = createMessage(GROUP_C);
        final CountDownLatch messageProcessingLatch = new CountDownLatch(3);
        final Set<Message> processingMessages = ConcurrentHashMap.newKeySet();

        // act
        runBrokerProcessMessageOnThread(
            broker,
            buildMessageSupplier(firstMessage, secondMessage, thirdMessage),
            message ->
                CompletableFuture.runAsync(
                    () -> {
                        log.info("Processing message: {}", message.messageId());
                        processingMessages.add(message);
                        messageProcessingLatch.countDown();
                        if (message == thirdMessage) {
                            try {
                                Thread.sleep(Long.MAX_VALUE);
                            } catch (final InterruptedException interruptedException) {
                                //expected
                            }
                        }
                        log.info("Done");
                    },
                    messageProcessorExecutorService
                ),
            brokerExecutorService
        );

        // assert
        assertThat(messageProcessingLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(processingMessages).containsExactlyInAnyOrder(firstMessage, secondMessage, thirdMessage);
    }

    @Nested
    class PurgeExtraMessagesOnError {

        @Test
        void onMessageFailureWillRemoveOtherMessagesInGroup() throws Exception {
            // arrange
            final GroupingMessageBroker broker = new GroupingMessageBroker(
                ImmutableGroupingMessageBrokerProperties
                    .builder()
                    .from(DEFAULT_PROPERTIES)
                    .concurrencyLevel(1)
                    .purgeExtraMessagesInGroupOnError(true)
                    .build()
            );
            final Message firstMessage = createMessage(GROUP_A);
            final Message secondMessage = createMessage(GROUP_A);
            final Message thirdMessage = createMessage(GROUP_C);
            final CountDownLatch messageProcessingLatch = new CountDownLatch(2);
            final Set<Message> processingMessages = ConcurrentHashMap.newKeySet();

            // act
            runBrokerProcessMessageOnThread(
                broker,
                buildMessageSupplier(firstMessage, secondMessage, thirdMessage),
                message ->
                    CompletableFuture.runAsync(
                        () -> {
                            log.info("Processing message: {}", message.messageId());
                            processingMessages.add(message);
                            messageProcessingLatch.countDown();
                            if (message == firstMessage) {
                                throw new ExpectedTestException();
                            }
                            if (message == thirdMessage) {
                                try {
                                    Thread.sleep(Long.MAX_VALUE);
                                } catch (final InterruptedException interruptedException) {
                                    //expected
                                }
                            }
                            log.info("Done");
                        },
                        messageProcessorExecutorService
                    ),
                brokerExecutorService
            );

            // assert
            assertThat(messageProcessingLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(processingMessages).containsExactlyInAnyOrder(firstMessage, thirdMessage);
        }
    }

    @Test
    void messageRetrievalFailingFutureWillBackoffBeforeObtainingMore() throws Exception {
        // arrange
        final GroupingMessageBroker broker = new GroupingMessageBroker(
            ImmutableGroupingMessageBrokerProperties.builder().from(DEFAULT_PROPERTIES).errorBackoffTime(Duration.ofSeconds(1)).build()
        );
        final Message expectedMessage = createMessage(GROUP_A);
        final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
        final Set<Message> processingMessages = ConcurrentHashMap.newKeySet();
        final AtomicInteger messageCount = new AtomicInteger(0);

        // act
        final long startTime = System.currentTimeMillis();
        runBrokerProcessMessageOnThread(
            broker,
            () -> {
                final int currentCount = messageCount.getAndIncrement();
                if (currentCount == 0) {
                    try {
                        Thread.sleep(1000);
                        return CompletableFutureUtils.completedExceptionally(new ExpectedTestException());
                    } catch (InterruptedException e) {
                        return new CompletableFuture<>();
                    }
                } else if (currentCount == 1) {
                    return CompletableFuture.completedFuture(expectedMessage);
                } else {
                    return new CompletableFuture<>();
                }
            },
            message ->
                CompletableFuture.runAsync(
                    () -> {
                        log.info("Processing message: {}", message.messageId());
                        processingMessages.add(message);
                        messageProcessingLatch.countDown();
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (final InterruptedException interruptedException) {
                            //expected
                        }
                        log.info("Done");
                    },
                    messageProcessorExecutorService
                ),
            brokerExecutorService
        );

        // assert
        assertThat(messageProcessingLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(processingMessages).containsExactlyInAnyOrder(expectedMessage);
        assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(Duration.ofSeconds(1).toMillis());
    }

    @Nested
    class ProcessCachedMessagesOnShutdown {

        @Test
        void willProcessExtraMessagesOnShutdown() throws Exception {
            // arrange
            final GroupingMessageBroker broker = new GroupingMessageBroker(
                ImmutableGroupingMessageBrokerProperties
                    .builder()
                    .from(DEFAULT_PROPERTIES)
                    .processCachedMessagesOnShutdown(true)
                    .errorBackoffTime(Duration.ofSeconds(1))
                    .build()
            );
            final Message firstMessage = createMessage(GROUP_A);
            final Message secondMessage = createMessage(GROUP_A);
            final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
            final CountDownLatch secondMessageProcessed = new CountDownLatch(1);
            final CountDownLatch canProcessMoreMessages = new CountDownLatch(1);
            final Set<Message> processingMessages = ConcurrentHashMap.newKeySet();
            final AtomicBoolean hasProcessedSecondMessage = new AtomicBoolean(false);

            // act
            runBrokerProcessMessageOnThread(
                broker,
                buildMessageSupplier(firstMessage, secondMessage),
                message ->
                    CompletableFuture.runAsync(
                        () -> {
                            log.info("Processing message: {}", message.messageId());
                            processingMessages.add(message);
                            if (message == firstMessage) {
                                messageProcessingLatch.countDown();

                                try {
                                    canProcessMoreMessages.await(5, TimeUnit.SECONDS);
                                } catch (final InterruptedException interruptedException) {
                                    //expected
                                }
                            } else if (message == secondMessage) {
                                hasProcessedSecondMessage.set(true);
                                secondMessageProcessed.countDown();
                            }
                            log.info("Done");
                        },
                        messageProcessorExecutorService
                    ),
                brokerExecutorService
            );

            // assert
            assertThat(messageProcessingLatch.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(500);
            assertThat(hasProcessedSecondMessage).isFalse();
            brokerExecutorService.shutdownNow();
            canProcessMoreMessages.countDown();
            assertThat(secondMessageProcessed.await(5, TimeUnit.SECONDS)).isTrue();
        }

        @Test
        void willNotProcessExtraMessagesOnShutdown() throws Exception {
            // arrange
            final GroupingMessageBroker broker = new GroupingMessageBroker(
                ImmutableGroupingMessageBrokerProperties
                    .builder()
                    .from(DEFAULT_PROPERTIES)
                    .processCachedMessagesOnShutdown(false)
                    .errorBackoffTime(Duration.ofSeconds(1))
                    .build()
            );
            final Message firstMessage = createMessage(GROUP_A);
            final Message secondMessage = createMessage(GROUP_A);
            final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
            final Set<Message> processingMessages = ConcurrentHashMap.newKeySet();

            // act
            final Future<?> future = runBrokerProcessMessageOnThread(
                broker,
                buildMessageSupplier(firstMessage, secondMessage),
                message ->
                    CompletableFuture.runAsync(
                        () -> {
                            log.info("Processing message: {}", message.messageId());
                            processingMessages.add(message);
                            if (message == firstMessage) {
                                messageProcessingLatch.countDown();

                                try {
                                    Thread.sleep(Long.MAX_VALUE);
                                } catch (final InterruptedException interruptedException) {
                                    //expected
                                }
                            }
                            log.info("Done");
                        },
                        messageProcessorExecutorService
                    ),
                brokerExecutorService
            );

            // assert
            assertThat(messageProcessingLatch.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(500);
            brokerExecutorService.shutdownNow();
            future.get(5, TimeUnit.SECONDS);
            assertThat(processingMessages).doesNotContain(secondMessage);
        }
    }

    private Message createMessage(final String groupId) {
        final int id = messageId.getAndIncrement();
        return Message
            .builder()
            .messageId("" + id)
            .attributes(Collections.singletonMap(MessageSystemAttributeName.MESSAGE_GROUP_ID, groupId))
            .body("body " + id)
            .build();
    }

    private Supplier<CompletableFuture<Message>> buildMessageSupplier(final Message... messages) {
        final AtomicInteger index = new AtomicInteger(0);
        return () -> {
            final int i = index.getAndIncrement();
            if (i >= messages.length) {
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture(messages[i]);
        };
    }
}
