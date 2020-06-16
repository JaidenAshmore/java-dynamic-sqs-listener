package com.jashmore.sqs.retriever.prefetch;

import static com.jashmore.sqs.util.thread.ThreadTestUtils.waitUntilThreadInState;
import static java.lang.Thread.State.TERMINATED;
import static java.lang.Thread.State.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Slf4j
@ExtendWith(MockitoExtension.class)
class PrefetchingMessageFutureConsumerQueueTest {
    @Mock
    private CompletableFuture<Message> messageCompletableFuture;

    private Thread thread = null;

    @AfterEach
    void setUp() {
        if (thread != null) {
            thread.interrupt();
        }
        thread = null;
    }

    @Test
    void whenMessagesInQueueHitsCapacityLimitFutureCallsAreBlocked() throws InterruptedException {
        // arrange
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(1);
        prefetchingMessageRetriever.pushMessage(Message.builder().build());

        // act
        final Thread thread = new Thread(() -> {
            try {
                prefetchingMessageRetriever.pushMessage(Message.builder().build());
            } catch (InterruptedException e) {
                // do nothing
            }
        });
        thread.start();

        // assert
        waitUntilThreadInState(thread, WAITING);
    }

    @Test
    void whenMessagesInQueueHitsCapacityLimitItWillStopWaitingWhenACompletableFutureIsProvided() throws InterruptedException {
        // arrange
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(1);
        prefetchingMessageRetriever.pushMessage(Message.builder().build());
        thread = new Thread(() -> {
            try {
                prefetchingMessageRetriever.pushMessage(Message.builder().build());
            } catch (InterruptedException e) {
                // do nothing
            }
        });
        thread.start();
        waitUntilThreadInState(thread, WAITING);

        // act
        prefetchingMessageRetriever.pushCompletableFuture(new CompletableFuture<>());

        // assert
        waitUntilThreadInState(thread, TERMINATED);
    }

    @Test
    void addingCompletableFutureAndThenMessageWillCompleteFutureWithThatMessage() throws InterruptedException {
        // arrange
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(1);
        final Message message = Message.builder().build();

        // act
        prefetchingMessageRetriever.pushMessage(message);
        prefetchingMessageRetriever.pushCompletableFuture(messageCompletableFuture);

        // assert
        verify(messageCompletableFuture).complete(message);
    }

    @Test
    void addingMessageAndThenCompletableWillCompleteFutureWithThatMessage() throws InterruptedException {
        // arrange
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(1);
        final Message message = Message.builder().build();

        // act
        prefetchingMessageRetriever.pushCompletableFuture(messageCompletableFuture);
        prefetchingMessageRetriever.pushMessage(message);

        // assert
        verify(messageCompletableFuture).complete(message);
    }

    @Test
    void allMessagesAndCompletableFuturesAreResolvedWhenSubmittingMany() throws Exception {
        // arrange
        final int totalMessages = 1000;
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(20);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final Set<String> futuresCompleted = Sets.newConcurrentHashSet();
        final Set<String> messagesCompleted = Sets.newConcurrentHashSet();
        final List<CompletableFuture<?>> allFutures = Lists.newLinkedList();

        // act
        executorService.submit(() -> {
            IntStream.range(0, totalMessages)
                    .mapToObj(String::valueOf)
                    .map(index -> Message.builder().body(index).build())
                    .forEach(message -> {
                        try {
                            prefetchingMessageRetriever.pushMessage(message);
                        } catch (final InterruptedException interruptedException) {
                            // do nothing
                        }
                    });

            log.debug("Added all messages");
        });
        executorService.submit(() -> {
            IntStream.range(0, totalMessages)
                    .mapToObj(String::valueOf)
                    .forEach(index -> {
                        final CompletableFuture<Message> completableFuture = new CompletableFuture<>();
                        allFutures.add(completableFuture);
                        completableFuture
                                .thenApply(message -> {
                                    futuresCompleted.add(index);
                                    log.debug("Matched message {} with future {}", message.body(), index);
                                    messagesCompleted.add(message.body());
                                    return message;
                                });
                        prefetchingMessageRetriever.pushCompletableFuture(completableFuture);
                    });
            log.debug("Added all futures");
        });
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture<?>[0])).get(1, TimeUnit.SECONDS);

        // assert
        assertThat(futuresCompleted).hasSize(totalMessages);
        assertThat(messagesCompleted).hasSize(totalMessages);
    }

    @Test
    void drainingQueueWhenExtraCompletableFuturesWillReturnThem() {
        // arrange
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(1);
        final CompletableFuture<Message> firstCompletableFuture = new CompletableFuture<>();
        final CompletableFuture<Message> secondCompletableFuture = new CompletableFuture<>();
        prefetchingMessageRetriever.pushCompletableFuture(firstCompletableFuture);
        prefetchingMessageRetriever.pushCompletableFuture(secondCompletableFuture);

        // act
        final QueueDrain drainedQueues = prefetchingMessageRetriever.drain();

        // assert
        assertThat(drainedQueues.getFuturesWaitingForMessages()).containsExactly(firstCompletableFuture, secondCompletableFuture);
        assertThat(drainedQueues.getMessagesAvailableForProcessing()).isEmpty();
    }

    @Test
    void drainingQueueWhenExtraMessagesWillReturnThem() throws InterruptedException {
        // arrange
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(2);
        final Message firstMessage = Message.builder().body("first").build();
        final Message secondMessage = Message.builder().body("second").build();
        prefetchingMessageRetriever.pushMessage(firstMessage);
        prefetchingMessageRetriever.pushMessage(secondMessage);

        // act
        final QueueDrain drainedQueues = prefetchingMessageRetriever.drain();

        // assert
        assertThat(drainedQueues.getFuturesWaitingForMessages()).isEmpty();
        assertThat(drainedQueues.getMessagesAvailableForProcessing()).containsExactly(firstMessage, secondMessage);
    }

    @Test
    void gettingBatchSizeWillReturnNumberOfMessagesInTheQueue() throws InterruptedException {
        // arrange
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(2);
        final Message firstMessage = Message.builder().body("first").build();
        final Message secondMessage = Message.builder().body("second").build();
        prefetchingMessageRetriever.pushMessage(firstMessage);
        prefetchingMessageRetriever.pushMessage(secondMessage);

        // act
        final int numberOfBatchedMessages = prefetchingMessageRetriever.getNumberOfBatchedMessages();

        // assert
        assertThat(numberOfBatchedMessages).isEqualTo(2);
    }
}