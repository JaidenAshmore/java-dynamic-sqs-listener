package com.jashmore.sqs.retriever.prefetch.util;

import static com.jashmore.sqs.util.thread.ThreadTestUtils.waitUntilThreadInState;
import static java.lang.Thread.State.TERMINATED;
import static java.lang.Thread.State.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Slf4j
public class PrefetchingMessageFutureConsumerQueueTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CompletableFuture<Message> messageCompletableFuture;

    private Thread thread = null;

    @After
    public void setUp() {
        if (thread != null) {
            thread.interrupt();
        }
        thread = null;
    }

    @Test
    public void whenMessagesInQueueHitsCapacityLimitFutureCallsAreBlocked() throws InterruptedException {
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
    public void whenMessagesInQueueHitsCapacityLimitItWillStopWaitingWhenACompletableFutureIsProvided() throws InterruptedException {
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
    public void addingCompletableFutureAndThenMessageWillCompleteFutureWithThatMessage() throws InterruptedException {
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
    public void addingMessageAndThenCompletableWillCompleteFutureWithThatMessage() throws InterruptedException {
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
    public void allMessagesAndCompletableFuturesAreResolvedWhenSubmittingMany() throws Exception {
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
    public void drainingQueueWhenExtraCompletableFuturesWillReturnThem() {
        // arrange
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(1);
        final CompletableFuture<Message> firstCompletableFuture = new CompletableFuture<>();
        final CompletableFuture<Message> secondCompletableFuture = new CompletableFuture<>();
        prefetchingMessageRetriever.pushCompletableFuture(firstCompletableFuture);
        prefetchingMessageRetriever.pushCompletableFuture(secondCompletableFuture);

        // act
        final Pair<Queue<CompletableFuture<Message>>, Queue<Message>> drainedQueues = prefetchingMessageRetriever.drain();

        // assert
        assertThat(drainedQueues.getKey()).containsExactly(firstCompletableFuture, secondCompletableFuture);
        assertThat(drainedQueues.getValue()).isEmpty();
    }

    @Test
    public void drainingQueueWhenExtraMessagesWillReturnThem() throws InterruptedException {
        // arrange
        final PrefetchingMessageFutureConsumerQueue prefetchingMessageRetriever = new PrefetchingMessageFutureConsumerQueue(2);
        final Message firstMessage = Message.builder().body("first").build();
        final Message secondMessage = Message.builder().body("second").build();
        prefetchingMessageRetriever.pushMessage(firstMessage);
        prefetchingMessageRetriever.pushMessage(secondMessage);

        // act
        final Pair<Queue<CompletableFuture<Message>>, Queue<Message>> drainedQueues = prefetchingMessageRetriever.drain();

        // assert
        assertThat(drainedQueues.getKey()).isEmpty();
        assertThat(drainedQueues.getValue()).containsExactly(firstMessage, secondMessage);
    }

    @Test
    public void gettingBatchSizeWillReturnNumberOfMessagesInTheQueue() throws InterruptedException {
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