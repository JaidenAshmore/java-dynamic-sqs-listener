package com.jashmore.sqs.broker.concurrent;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.processor.MessageProcessingException;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentMessageBrokerTest {
    private static final StaticConcurrentMessageBrokerProperties DEFAULT_PROPERTIES = StaticConcurrentMessageBrokerProperties.builder()
            .concurrencyLevel(2)
            .preferredConcurrencyPollingRateInMilliseconds(100L)
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MessageRetriever messageRetriever;

    @Mock
    private MessageProcessor messageProcessor;

    @Mock
    private ConcurrentMessageBrokerProperties concurrentMessageBrokerProperties;

    private ExecutorService executorService;

    @Before
    public void setUp() {
        executorService = Executors.newCachedThreadPool();
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void shouldBeAbleToRunMultipleThreadsConcurrentlyForProcessingMessages() throws InterruptedException {
        // arrange
        final int concurrencyLevel = 5;
        final ConcurrentMessageBrokerProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .concurrencyLevel(concurrencyLevel)
                .build();
        final ConcurrentMessageBroker controller = new ConcurrentMessageBroker(messageRetriever, messageProcessor, properties);
        final CountDownLatch threadsProcessingLatch = new CountDownLatch(concurrencyLevel);
        final CountDownLatch continueProcessingLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            threadsProcessingLatch.countDown();
            continueProcessingLatch.await();
            return null;
        }).when(messageProcessor).processMessage(any(Message.class));
        when(messageRetriever.retrieveMessage())
                .thenReturn(Message.builder().build());

        // act
        final Future<?> controllerFuture = executorService.submit(controller);

        // assert
        threadsProcessingLatch.await(1, SECONDS);
        controllerFuture.cancel(true);
        continueProcessingLatch.countDown();
    }

    @Test
    public void noPermitsWillKeepPollingUntilAcquiredOrTimeout() throws InterruptedException {
        // arrange
        when(messageRetriever.retrieveMessage())
                .thenReturn(Message.builder().build());
        final ConcurrentMessageBroker concurrentMessageBroker = new ConcurrentMessageBroker(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties);
        final int concurrencyLevel = 0;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100L);
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(concurrencyLevel);

        // act
        final Future<?> controllerFuture = executorService.submit(concurrentMessageBroker);
        Thread.sleep(100 * 3);
        controllerFuture.cancel(true);

        // assert
        verify(messageRetriever, never()).retrieveMessage();
    }

    @Test
    public void allPermitsAcquiredWillKeepPollingUntilAcquiredOrTimeout() throws InterruptedException {
        // arrange
        when(messageRetriever.retrieveMessage())
                .thenReturn(Message.builder().build());
        final ConcurrentMessageBroker controller = new ConcurrentMessageBroker(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties);
        final int concurrencyLevel = 1;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100L);
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(concurrencyLevel);
        final CountDownLatch testFinishedLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            testFinishedLatch.await(1, SECONDS);
            return null;
        }).when(messageProcessor).processMessage(any(Message.class));

        // act
        final Future<?> controllerFuture = Executors.newSingleThreadExecutor().submit(controller);
        Thread.sleep(100 * 3);

        // assert
        verify(messageRetriever, times(1)).retrieveMessage();
        verify(concurrentMessageBrokerProperties, times(4)).getConcurrencyLevel();

        // cleanup
        controllerFuture.cancel(true);
        testFinishedLatch.countDown();
    }

    @Test
    public void exceptionThrownWhileRetrievingMessageWillStillAllowMoreMessagesToRetrieved() throws InterruptedException {
        // arrange
        final ConcurrentMessageBroker concurrentMessageBroker = new ConcurrentMessageBroker(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties);
        final int concurrencyLevel = 1;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100L);
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(concurrencyLevel);
        final CountDownLatch testFinishedLatch = new CountDownLatch(1);
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            messageProcessedLatch.countDown();
            testFinishedLatch.await(1, SECONDS);
            return null;
        }).when(messageProcessor).processMessage(any(Message.class));
        when(messageRetriever.retrieveMessage())
                .thenThrow(new RuntimeException("Expected Test Exception"))
                .thenReturn(Message.builder().build());

        // act
        final Future<?> controllerFuture = executorService.submit(concurrentMessageBroker);
        messageProcessedLatch.await(1, SECONDS);

        // assert
        verify(messageRetriever, times(2)).retrieveMessage();

        // cleanup
        controllerFuture.cancel(true);
        testFinishedLatch.countDown();
    }

    @Test
    public void exceptionThrownWhileGettingPropertiesWillStillAllowMoreMessagesToRetrieved() throws InterruptedException {
        // arrange
        final ConcurrentMessageBroker concurrentMessageBroker = new ConcurrentMessageBroker(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties);
        final int concurrencyLevel = 1;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100L);
        when(concurrentMessageBrokerProperties.getErrorBackoffTimeInMilliseconds()).thenReturn(1L);
        when(concurrentMessageBrokerProperties.getConcurrencyLevel())
                .thenThrow(new RuntimeException("Expected Test Exception"))
                .thenReturn(concurrencyLevel);
        final CountDownLatch testFinishedLatch = new CountDownLatch(1);
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            messageProcessedLatch.countDown();
            testFinishedLatch.await(1, SECONDS);
            return null;
        }).when(messageProcessor).processMessage(any(Message.class));
        when(messageRetriever.retrieveMessage())
                .thenReturn(Message.builder().build());

        // act
        final Future<?> controllerFuture = executorService.submit(concurrentMessageBroker);
        assertThat(messageProcessedLatch.await(1, SECONDS)).isTrue();

        // assert
        verify(messageRetriever, times(1)).retrieveMessage();

        // cleanup
        controllerFuture.cancel(true);
        testFinishedLatch.countDown();
    }

    @Test
    public void exceptionThrownProcessingMessageDoesNotAffectOthers() throws InterruptedException {
        // arrange
        final ConcurrentMessageBroker controller = new ConcurrentMessageBroker(messageRetriever, messageProcessor, concurrentMessageBrokerProperties);
        final int concurrencyLevel = 1;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100L);
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(concurrencyLevel);
        final CountDownLatch testFinishedLatch = new CountDownLatch(1);
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        final AtomicBoolean isFirst = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (isFirst.get()) {
                isFirst.set(false);
                throw new MessageProcessingException("error");
            }

            messageProcessedLatch.countDown();
            testFinishedLatch.await(1, SECONDS);

            return null;
        }).when(messageProcessor).processMessage(any(Message.class));
        when(messageRetriever.retrieveMessage())
                .thenReturn(Message.builder().build())
                .thenReturn(Message.builder().build());

        // act
        final Future<?> controllerFuture = Executors.newSingleThreadExecutor().submit(controller);
        messageProcessedLatch.await(1, SECONDS);

        // assert
        verify(messageRetriever, times(2)).retrieveMessage();

        // cleanup
        controllerFuture.cancel(true);
        testFinishedLatch.countDown();
    }

    @Test
    public void stoppingBrokerWithInterruptsWillStopRunningThreads() throws InterruptedException {
        // arrange
        final ConcurrentMessageBroker controller = new ConcurrentMessageBroker(messageRetriever, messageProcessor, concurrentMessageBrokerProperties);
        final int concurrencyLevel = 1;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100L);
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(concurrencyLevel);
        final CountDownLatch testFinishedLatch = new CountDownLatch(1);
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        final AtomicBoolean messageProcessed = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        doAnswer(invocation -> {
            messageProcessedLatch.countDown();
            semaphore.acquire();
            messageProcessed.set(true);
            return null;
        }).when(messageProcessor).processMessage(any(Message.class));
        when(messageRetriever.retrieveMessage())
                .thenReturn(Message.builder().build());

        // act
        final Future<?> controllerFuture = Executors.newSingleThreadExecutor().submit(controller);
        messageProcessedLatch.await(1, SECONDS);
        controllerFuture.cancel(true);
        testFinishedLatch.countDown();

        // assert
        assertThat(messageProcessed.get()).isFalse();
    }

    @Test
    public void whenPropertiesContainThreadNameThreadsForProcessingMessagesShouldContainThatThreadName() throws Exception {
        // arrange
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(1);
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(10_000L);
        when(concurrentMessageBrokerProperties.getThreadNameFormat()).thenReturn("my-thread-%d");
        final AtomicReference<String> actualMessageProcessingThreadName = new AtomicReference<>();
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(messageRetriever, messageProcessor, concurrentMessageBrokerProperties);
        when(messageRetriever.retrieveMessage()).thenReturn(Message.builder().build());

        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        doAnswer((invocation) -> {
            actualMessageProcessingThreadName.set(Thread.currentThread().getName());
            messageProcessedLatch.countDown();
            testCompletedLatch.await();
            return null;
        }).when(messageProcessor).processMessage(any());

        // act
        final Future<?> brokerFuture = Executors.newSingleThreadExecutor().submit(broker);
        messageProcessedLatch.await(1, SECONDS);
        brokerFuture.cancel(true);
        testCompletedLatch.countDown();

        // assert
        assertThat(actualMessageProcessingThreadName.get()).startsWith("my-thread-");
    }

    @Test
    public void errorBuildingThreadNameWillUsedDefaultCachedExecutorServiceName() throws Exception {
        // arrange
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(1);
        when(concurrentMessageBrokerProperties.getThreadNameFormat()).thenAnswer((invocation) -> new RuntimeException("Expected Test Exception"));
        final AtomicReference<String> actualMessageProcessingThreadName = new AtomicReference<>();
        final ConcurrentMessageBroker broker = new ConcurrentMessageBroker(messageRetriever, messageProcessor, concurrentMessageBrokerProperties);
        when(messageRetriever.retrieveMessage()).thenReturn(Message.builder().build());

        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        doAnswer((invocation) -> {
            actualMessageProcessingThreadName.set(Thread.currentThread().getName());
            messageProcessedLatch.countDown();
            testCompletedLatch.await();
            return null;
        }).when(messageProcessor).processMessage(any());

        // act
        final Future<?> brokerFuture = Executors.newSingleThreadExecutor().submit(broker);
        messageProcessedLatch.await(1, SECONDS);
        brokerFuture.cancel(true);
        testCompletedLatch.countDown();

        // assert
        assertThat(actualMessageProcessingThreadName.get()).startsWith("pool-");
    }
}