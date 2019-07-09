package com.jashmore.sqs.broker.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessingException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class ConcurrentMessageBrokerTest {
    private static final Function<Message, CompletableFuture<?>> MESSAGE_NO_OP = message -> CompletableFuture.completedFuture(null);
    private static final StaticConcurrentMessageBrokerProperties DEFAULT_PROPERTIES = StaticConcurrentMessageBrokerProperties.builder()
            .concurrencyLevel(1)
            .preferredConcurrencyPollingRateInMilliseconds(100L)
            .errorBackoffTimeInMilliseconds(0L)
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Supplier<CompletableFuture<Message>> messageSupplier;

    private ExecutorService brokerExecutorService;

    @Before
    public void setUp() {
        brokerExecutorService = Executors.newCachedThreadPool();
    }

    @After
    public void tearDown() {
        brokerExecutorService.shutdownNow();
    }

    @Test
    public void shouldBeAbleToProcessMultipleMessagesConcurrently() throws InterruptedException {
        // arrange
        final int concurrencyLevel = 5;
        final ConcurrentMessageBrokerProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .concurrencyLevel(concurrencyLevel)
                .build();
        final CountDownLatch messagesProcessingLatch = new CountDownLatch(concurrencyLevel);
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        runBrokerProcessMessageOnThread(
                broker,
                () -> CompletableFuture.completedFuture(Message.builder().build()),
                processingMessageWillBlockUntilInterrupted(messagesProcessingLatch)
        );

        // assert
        assertThat(messagesProcessingLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    public void whenNoAvailableConcurrencyNoMessagesWillBeRequested() throws InterruptedException {
        // arrange
        final long concurrencyPollingRateInMs = 100L;
        final ConcurrentMessageBrokerProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .preferredConcurrencyPollingRateInMilliseconds(concurrencyPollingRateInMs)
                .concurrencyLevel(0)
                .build();
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        runBrokerProcessMessageOnThread(broker, messageSupplier, MESSAGE_NO_OP);
        Thread.sleep(concurrencyPollingRateInMs * 3);

        // assert
        verify(messageSupplier, never()).get();
    }

    @Test
    public void whenConcurrencyLimitReachedItWillSpinForChangesInConcurrencyWhileWaiting() throws Exception {
        // arrange
        final long concurrencyPollingRateInMs = 100L;
        final AtomicInteger numberTimesConcurrencyPolled = new AtomicInteger(0);
        final ConcurrentMessageBrokerProperties properties = mock(ConcurrentMessageBrokerProperties.class);
        when(properties.getConcurrencyPollingRateInMilliseconds()).thenReturn(concurrencyPollingRateInMs);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        when(properties.getConcurrencyLevel())
                .thenAnswer((invocation) -> {
                    if (numberTimesConcurrencyPolled.incrementAndGet() == 3) {
                        countDownLatch.countDown();
                    }

                    return 0;
                });
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        runBrokerProcessMessageOnThread(broker, messageSupplier, processingMessageWillBlockUntilInterrupted());

        // assert
        assertThat(countDownLatch.await(concurrencyPollingRateInMs * 3, MILLISECONDS)).isTrue();
    }

    @Test
    public void willStopProcessingMessagesIfKeepProcessingMessagesReturnsFalse() throws Exception {
        // arrange
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(DEFAULT_PROPERTIES);

        // act
        final Future<?> future = runBrokerProcessMessageOnThread(broker, () -> false, messageSupplier, processingMessageWillBlockUntilInterrupted());
        future.get(1, SECONDS);

        // assert
        verify(messageSupplier, never()).get();
    }

    @Test
    public void exceptionThrownWhileRetrievingMessageWillStillAllowMoreMessagesToRetrieved() throws InterruptedException {
        // arrange
        when(messageSupplier.get())
                .thenThrow(new RuntimeException("Expected Test Exception"))
                .thenReturn(CompletableFuture.completedFuture(Message.builder().build()));
        final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(DEFAULT_PROPERTIES);

        // act
        runBrokerProcessMessageOnThread(broker, messageSupplier, processingMessageWillBlockUntilInterrupted(messageProcessingLatch));

        // assert
        assertThat(messageProcessingLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    public void messageRetrievalReturningExceptionalCompletableFutureStillAllowsMoreMessagesToBeProcessed() throws InterruptedException {
        // arrange
        when(messageSupplier.get())
                .thenReturn(CompletableFutureUtils.completedExceptionally(new RuntimeException("Expected Test Exception")))
                .thenReturn(CompletableFuture.completedFuture(Message.builder().build()));
        final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(DEFAULT_PROPERTIES);

        // act
        runBrokerProcessMessageOnThread(broker, messageSupplier, processingMessageWillBlockUntilInterrupted(messageProcessingLatch));

        // assert
        assertThat(messageProcessingLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    public void exceptionThrownWhileGettingPropertiesWillStillAllowMoreMessagesToRetrieved() throws InterruptedException {
        // arrange
        final ConcurrentMessageBrokerProperties properties = mock(ConcurrentMessageBrokerProperties.class);
        when(properties.getConcurrencyPollingRateInMilliseconds()).thenReturn(1L);
        when(properties.getConcurrencyLevel())
                .thenThrow(new RuntimeException("Expected Test Exception"))
                .thenReturn(1);
        when(messageSupplier.get())
                .thenReturn(CompletableFuture.completedFuture(Message.builder().build()));
        final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        runBrokerProcessMessageOnThread(broker, messageSupplier, processingMessageWillBlockUntilInterrupted(messageProcessingLatch));

        // assert
        assertThat(messageProcessingLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    public void exceptionThrownProcessingMessageDoesNotAffectOthersFromBeingRun() throws InterruptedException {
        // arrange
        when(messageSupplier.get())
                .thenReturn(CompletableFuture.completedFuture(Message.builder().build()));
        final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
        final AtomicBoolean isFirst = new AtomicBoolean(true);
        final Function<Message, CompletableFuture<?>> messageConsumer = processingMessageWillBlockUntilInterrupted(messageProcessingLatch, () -> {
            if (isFirst.get()) {
                isFirst.set(false);
                throw new MessageProcessingException("Expected Test Exception");
            }
        });
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(DEFAULT_PROPERTIES);

        // act
        runBrokerProcessMessageOnThread(broker, messageSupplier, messageConsumer);

        // assert
        assertThat(messageProcessingLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    public void threadInterruptedDuringBackoffShouldStopBroker() throws Exception {
        // arrange
        final long backoffTimeInMs = 6000L;
        final ConcurrentMessageBrokerProperties properties = mock(ConcurrentMessageBrokerProperties.class);
        when(properties.getConcurrencyLevel()).thenReturn(2);
        when(properties.getConcurrencyPollingRateInMilliseconds()).thenReturn(1000L);
        final CountDownLatch enteredBackoffSection = new CountDownLatch(1);
        when(properties.getErrorBackoffTimeInMilliseconds())
                .thenAnswer((invocation) -> {
                    enteredBackoffSection.countDown();
                    return backoffTimeInMs;
                });
        when(messageSupplier.get()).thenThrow(new RuntimeException("Expected Test Exception"));
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        final Future<?> brokerFuture = runBrokerProcessMessageOnThread(broker, messageSupplier, MESSAGE_NO_OP);
        assertThat(enteredBackoffSection.await(1, SECONDS)).isTrue();
        brokerExecutorService.shutdownNow();

        // assert
        brokerFuture.get(backoffTimeInMs / 2, MILLISECONDS);
    }

    private Future<?> runBrokerProcessMessageOnThread(final MessageBroker broker,
                                                      final Supplier<CompletableFuture<Message>> messageRetriever,
                                                      final Function<Message, CompletableFuture<?>> messageConsumer) {
        return brokerExecutorService.submit(() -> {
            try {
                broker.processMessages(Executors.newCachedThreadPool(), messageRetriever, messageConsumer);
            } catch (InterruptedException e) {
                //ignore
            }
        });
    }

    private Future<?> runBrokerProcessMessageOnThread(final MessageBroker broker,
                                                      final BooleanSupplier keepProcessingMessages,
                                                      final Supplier<CompletableFuture<Message>> messageRetriever,
                                                      final Function<Message, CompletableFuture<?>> messageConsumer) {
        return brokerExecutorService.submit(() -> {
            try {
                broker.processMessages(Executors.newCachedThreadPool(), keepProcessingMessages, messageRetriever, messageConsumer);
            } catch (InterruptedException e) {
                //ignore
            }
        });
    }

    private Function<Message, CompletableFuture<?>> processingMessageWillBlockUntilInterrupted() {
        return processingMessageWillBlockUntilInterrupted(null);
    }

    private Function<Message, CompletableFuture<?>> processingMessageWillBlockUntilInterrupted(final CountDownLatch messageProcessingLatch) {
        return processingMessageWillBlockUntilInterrupted(messageProcessingLatch, () -> {
        });
    }

    private Function<Message, CompletableFuture<?>> processingMessageWillBlockUntilInterrupted(final CountDownLatch messageProcessingLatch,
                                                                                               final Runnable runnableCalledOnMessageProcessing) {

        return (message) -> CompletableFuture.runAsync(() -> {
            runnableCalledOnMessageProcessing.run();
            if (messageProcessingLatch != null) {
                messageProcessingLatch.countDown();
            }
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (final InterruptedException interruptedException) {
                //expected
            }
        });
    }
}