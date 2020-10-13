package com.jashmore.sqs.broker.concurrent;

import static com.jashmore.sqs.broker.util.MessageBrokerTestUtils.processingMessageWillBlockUntilInterrupted;
import static com.jashmore.sqs.broker.util.MessageBrokerTestUtils.runBrokerProcessMessageOnThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.processor.MessageProcessingException;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
@ExtendWith(MockitoExtension.class)
class ConcurrentMessageBrokerTest {

    private static final Function<Message, CompletableFuture<?>> MESSAGE_NO_OP = message -> CompletableFuture.completedFuture(null);
    private static final StaticConcurrentMessageBrokerProperties DEFAULT_PROPERTIES = StaticConcurrentMessageBrokerProperties
        .builder()
        .concurrencyLevel(1)
        .preferredConcurrencyPollingRate(Duration.ofMillis(100))
        .errorBackoffTime(Duration.ofSeconds(0))
        .build();

    @Mock
    private Supplier<CompletableFuture<Message>> messageSupplier;

    private ExecutorService brokerExecutorService;
    private ExecutorService messageProcessorExecutorService;

    @BeforeEach
    void setUp() {
        brokerExecutorService = Executors.newCachedThreadPool();
        messageProcessorExecutorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        brokerExecutorService.shutdownNow();
        messageProcessorExecutorService.shutdownNow();
    }

    @Test
    void shouldBeAbleToProcessMultipleMessagesConcurrently() throws InterruptedException {
        // arrange
        final int concurrencyLevel = 5;
        final ConcurrentMessageBrokerProperties properties = DEFAULT_PROPERTIES.toBuilder().concurrencyLevel(concurrencyLevel).build();
        final CountDownLatch messagesProcessingLatch = new CountDownLatch(concurrencyLevel);
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        runBrokerProcessMessageOnThread(
            broker,
            () -> CompletableFuture.completedFuture(Message.builder().build()),
            processingMessageWillBlockUntilInterrupted(messagesProcessingLatch, messageProcessorExecutorService),
            brokerExecutorService
        );

        // assert
        assertThat(messagesProcessingLatch.await(30, SECONDS)).isTrue();
    }

    @Test
    void whenNoAvailableConcurrencyNoMessagesWillBeRequested() throws InterruptedException {
        // arrange
        final long concurrencyPollingRateInMs = 100L;
        final ConcurrentMessageBrokerProperties properties = DEFAULT_PROPERTIES
            .toBuilder()
            .preferredConcurrencyPollingRate(Duration.ofMillis(concurrencyPollingRateInMs))
            .concurrencyLevel(0)
            .build();
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        runBrokerProcessMessageOnThread(broker, messageSupplier, MESSAGE_NO_OP, brokerExecutorService);
        Thread.sleep(concurrencyPollingRateInMs * 3);

        // assert
        verify(messageSupplier, never()).get();
    }

    @Test
    void whenConcurrencyLimitReachedItWillSpinForChangesInConcurrencyWhileWaiting() throws Exception {
        // arrange
        final long concurrencyPollingRateInMs = 100L;
        final AtomicInteger numberTimesConcurrencyPolled = new AtomicInteger(0);
        final ConcurrentMessageBrokerProperties properties = mock(ConcurrentMessageBrokerProperties.class);
        when(properties.getConcurrencyPollingRate()).thenReturn(Duration.ofMillis(concurrencyPollingRateInMs));
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        when(properties.getConcurrencyLevel())
            .thenAnswer(
                invocation -> {
                    if (numberTimesConcurrencyPolled.incrementAndGet() == 3) {
                        countDownLatch.countDown();
                    }

                    return 0;
                }
            );
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        runBrokerProcessMessageOnThread(
            broker,
            messageSupplier,
            processingMessageWillBlockUntilInterrupted(messageProcessorExecutorService),
            brokerExecutorService
        );

        // assert
        assertThat(countDownLatch.await(concurrencyPollingRateInMs * 3, MILLISECONDS)).isTrue();
    }

    @Test
    void willStopProcessingMessagesIfKeepProcessingMessagesReturnsFalse() throws Exception {
        // arrange
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(DEFAULT_PROPERTIES);

        // act
        final Future<?> future = runBrokerProcessMessageOnThread(
            broker,
            () -> false,
            messageSupplier,
            processingMessageWillBlockUntilInterrupted(messageProcessorExecutorService),
            brokerExecutorService
        );
        future.get(30, SECONDS);

        // assert
        verify(messageSupplier, never()).get();
    }

    @Test
    void exceptionThrownWhileRetrievingMessageWillStillAllowMoreMessagesToRetrieved() throws InterruptedException {
        // arrange
        when(messageSupplier.get())
            .thenThrow(new ExpectedTestException())
            .thenReturn(CompletableFuture.completedFuture(Message.builder().build()));
        final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(DEFAULT_PROPERTIES);

        // act
        runBrokerProcessMessageOnThread(
            broker,
            messageSupplier,
            processingMessageWillBlockUntilInterrupted(messageProcessingLatch, messageProcessorExecutorService),
            brokerExecutorService
        );

        // assert
        assertThat(messageProcessingLatch.await(30, SECONDS)).isTrue();
    }

    @Test
    void messageRetrievalReturningExceptionalCompletableFutureStillAllowsMoreMessagesToBeProcessed() throws InterruptedException {
        // arrange
        when(messageSupplier.get())
            .thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()))
            .thenReturn(CompletableFuture.completedFuture(Message.builder().build()));
        final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(DEFAULT_PROPERTIES);

        // act
        runBrokerProcessMessageOnThread(
            broker,
            messageSupplier,
            processingMessageWillBlockUntilInterrupted(messageProcessingLatch, messageProcessorExecutorService),
            brokerExecutorService
        );

        // assert
        assertThat(messageProcessingLatch.await(30, SECONDS)).isTrue();
    }

    @Test
    void exceptionThrownWhileGettingPropertiesWillStillAllowMoreMessagesToRetrieved() throws InterruptedException {
        // arrange
        final ConcurrentMessageBrokerProperties properties = mock(ConcurrentMessageBrokerProperties.class);
        when(properties.getConcurrencyPollingRate()).thenReturn(Duration.ofMillis(1));
        when(properties.getConcurrencyLevel()).thenThrow(new ExpectedTestException()).thenReturn(1);
        when(messageSupplier.get()).thenReturn(CompletableFuture.completedFuture(Message.builder().build()));
        final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        runBrokerProcessMessageOnThread(
            broker,
            messageSupplier,
            processingMessageWillBlockUntilInterrupted(messageProcessingLatch, messageProcessorExecutorService),
            brokerExecutorService
        );

        // assert
        assertThat(messageProcessingLatch.await(30, SECONDS)).isTrue();
    }

    @Test
    void exceptionThrownProcessingMessageDoesNotAffectOthersFromBeingRun() throws InterruptedException {
        // arrange
        when(messageSupplier.get()).thenReturn(CompletableFuture.completedFuture(Message.builder().build()));
        final CountDownLatch messageProcessingLatch = new CountDownLatch(1);
        final AtomicBoolean isFirst = new AtomicBoolean(true);
        final Function<Message, CompletableFuture<?>> messageConsumer = processingMessageWillBlockUntilInterrupted(
            messageProcessingLatch,
            () -> {
                if (isFirst.get()) {
                    isFirst.set(false);
                    throw new MessageProcessingException("Expected Test Exception");
                }
            },
            messageProcessorExecutorService
        );
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(DEFAULT_PROPERTIES);

        // act
        runBrokerProcessMessageOnThread(broker, messageSupplier, messageConsumer, brokerExecutorService);

        // assert
        assertThat(messageProcessingLatch.await(30, SECONDS)).isTrue();
    }

    @Test
    void threadInterruptedDuringBackoffShouldStopBroker() throws Exception {
        // arrange
        final long backoffTimeInMs = 6000L;
        final ConcurrentMessageBrokerProperties properties = mock(ConcurrentMessageBrokerProperties.class);
        when(properties.getConcurrencyLevel()).thenReturn(2);
        when(properties.getConcurrencyPollingRate()).thenReturn(Duration.ofMillis(1000));
        final CountDownLatch enteredBackoffSection = new CountDownLatch(1);
        when(properties.getErrorBackoffTime())
            .thenAnswer(
                invocation -> {
                    enteredBackoffSection.countDown();
                    return Duration.ofMillis(backoffTimeInMs);
                }
            );
        when(messageSupplier.get()).thenThrow(new ExpectedTestException());
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(properties);

        // act
        final Future<?> brokerFuture = runBrokerProcessMessageOnThread(broker, messageSupplier, MESSAGE_NO_OP, brokerExecutorService);
        assertThat(enteredBackoffSection.await(30, SECONDS)).isTrue();
        brokerExecutorService.shutdownNow();

        // assert
        brokerFuture.get(backoffTimeInMs / 2, MILLISECONDS);
    }
}
