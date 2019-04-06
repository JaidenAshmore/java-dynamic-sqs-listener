package com.jashmore.sqs.broker.concurrent;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.MessageProcessingException;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConcurrentMessageBrokerTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MessageRetriever messageRetriever;

    @Mock
    private MessageProcessor messageProcessor;

    @Mock
    private ConcurrentMessageBrokerProperties concurrentMessageBrokerProperties;

    @Test
    public void concurrentBrokerCanBeBuiltCorrectly() {
        // act
        new ConcurrentMessageBroker(messageRetriever, messageProcessor, Executors.newCachedThreadPool(), concurrentMessageBrokerProperties);
    }

    @Test
    public void shouldBeAbleToRunMultipleThreadsConcurrentlyForProcessingMessages() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        when(messageRetriever.retrieveMessage())
                .thenReturn(Message.builder().build());
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        final ConcurrentMessageBroker.Controller controller = new ConcurrentMessageBroker.ConcurrentThreadController(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties, completableFuture);
        final int concurrencyLevel = 5;
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(concurrencyLevel);
        final CountDownLatch threadsProcessingLatch = new CountDownLatch(concurrencyLevel);
        final CountDownLatch continueProcessingLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            threadsProcessingLatch.countDown();
            continueProcessingLatch.await();
            return null;
        }).when(messageProcessor).processMessage(any(Message.class));

        // act
        final Future<?> controllerFuture = Executors.newSingleThreadExecutor().submit(controller);

        // assert
        threadsProcessingLatch.await(1, SECONDS);

        // cleanup
        controllerFuture.cancel(true);
        continueProcessingLatch.countDown();
        completableFuture.get(1, SECONDS);
    }

    @Test
    public void noPermitsWillKeepPollingUntilAcquiredOrTimeout() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        when(messageRetriever.retrieveMessage())
                .thenReturn(Message.builder().build());
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        final ConcurrentMessageBroker.Controller controller = new ConcurrentMessageBroker.ConcurrentThreadController(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties, completableFuture);
        final int concurrencyLevel = 0;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100);
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(concurrencyLevel);

        // act
        final Future<?> controllerFuture = Executors.newSingleThreadExecutor().submit(controller);
        Thread.sleep(100 * 3);

        // assert
        verify(messageRetriever, never()).retrieveMessage();

        // cleanup
        controllerFuture.cancel(true);
        completableFuture.get(1, SECONDS);
    }

    @Test
    public void allPermitsAcquiredWillKeepPollingUntilAcquiredOrTimeout() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        when(messageRetriever.retrieveMessage())
                .thenReturn(Message.builder().build());
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        final ConcurrentMessageBroker.Controller controller = new ConcurrentMessageBroker.ConcurrentThreadController(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties, completableFuture);
        final int concurrencyLevel = 1;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100);
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
        completableFuture.get(1, SECONDS);
    }

    @Test
    public void exceptionThrownWhileRetrievingMessageWillStillAllowMoreMessagesToRetrieved() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        final ConcurrentMessageBroker.Controller controller = new ConcurrentMessageBroker.ConcurrentThreadController(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties, completableFuture);
        final int concurrencyLevel = 1;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100);
        when(concurrentMessageBrokerProperties.getConcurrencyLevel()).thenReturn(concurrencyLevel);
        final CountDownLatch testFinishedLatch = new CountDownLatch(1);
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            messageProcessedLatch.countDown();
            testFinishedLatch.await(1, SECONDS);
            return null;
        }).when(messageProcessor).processMessage(any(Message.class));
        when(messageRetriever.retrieveMessage())
                .thenThrow(new RuntimeException("error"))
                .thenReturn(Message.builder().build());

        // act
        final Future<?> controllerFuture = Executors.newSingleThreadExecutor().submit(controller);
        messageProcessedLatch.await(1, SECONDS);

        // assert
        verify(messageRetriever, times(2)).retrieveMessage();

        // cleanup
        controllerFuture.cancel(true);
        testFinishedLatch.countDown();
        completableFuture.get(1, SECONDS);
    }

    @Test
    public void exceptionThrownProcessingMessageDoesNotAffectOthers() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        final ConcurrentMessageBroker.Controller controller = new ConcurrentMessageBroker.ConcurrentThreadController(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties, completableFuture);
        final int concurrencyLevel = 1;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100);
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
        completableFuture.get(1, SECONDS);
    }

    @Test
    public void stoppingBrokerWithInterruptsWillStopRunningThreads() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        final CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        final ConcurrentMessageBroker.Controller controller = new ConcurrentMessageBroker.ConcurrentThreadController(
                messageRetriever, messageProcessor, concurrentMessageBrokerProperties, completableFuture);
        final int concurrencyLevel = 1;
        when(concurrentMessageBrokerProperties.getPreferredConcurrencyPollingRateInMilliseconds()).thenReturn(100);
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
        controller.stopTriggered(true);
        controllerFuture.cancel(true);
        testFinishedLatch.countDown();
        completableFuture.get(1, SECONDS);

        // assert
        assertThat(messageProcessed.get()).isFalse();
    }
}