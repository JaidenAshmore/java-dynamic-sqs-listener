package com.jashmore.sqs.broker.singlethread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SingleThreadedMessageBrokerTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MessageRetriever messageRetriever;

    @Mock
    private MessageProcessor messageProcessor;

    @Mock
    private ExecutorService mockExecutorService;

    @Mock
    private Future<?> mockFuture;

    @Test
    public void controllerWillStopRunningWhenInterruptedDuringMessageRetrieval() throws InterruptedException, ExecutionException {
        // arrange
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, executorService, threadCompletedFuture);
        when(messageRetriever.retrieveMessage()).thenThrow(new InterruptedException());

        // act
        controller.run();

        // assert
        threadCompletedFuture.get();
    }

    @Test
    public void controllerWillTryAgainWhenRetrieverThrowsException() throws InterruptedException, ExecutionException {
        // arrange
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, executorService, threadCompletedFuture) {
            @Override
            void backoff() {
                // do not thread sleep
            }
        };
        when(messageRetriever.retrieveMessage())
                .thenThrow(new RuntimeException("error"))
                .thenThrow(new InterruptedException());

        // act
        controller.run();

        // assert
        threadCompletedFuture.get();
        verify(messageRetriever, times(2)).retrieveMessage();
    }

    @Test
    public void whenMessageIsRetrievedItWillBeProcessed() throws InterruptedException {
        // arrange
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, executorService, threadCompletedFuture);
        final Message message = Message.builder().build();
        when(messageRetriever.retrieveMessage())
                .thenReturn(message)
                .thenThrow(new InterruptedException());

        // act
        controller.run();

        // assert
        verify(messageProcessor).processMessage(message);
    }

    @Test
    public void exceptionProcessingMessageWillContinueToProcessOtherMessages() throws InterruptedException {
        // arrange
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, executorService, threadCompletedFuture) {
            @Override
            void backoff() {
                // do not sleep thread
            }
        };
        final Message firstMessage = Message.builder().body("test").build();
        final Message secondMessage = Message.builder().body("other").build();
        when(messageRetriever.retrieveMessage())
                .thenReturn(firstMessage)
                .thenReturn(secondMessage)
                .thenThrow(new InterruptedException());
        doThrow(new MessageProcessingException("error")).when(messageProcessor).processMessage(firstMessage);

        // act
        controller.run();

        // assert
        verify(messageProcessor).processMessage(secondMessage);
    }

    @Test
    public void interruptWhileProcessingMessageWillStillWaitForResult() throws InterruptedException, ExecutionException {
        // arrange
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, mockExecutorService, threadCompletedFuture);
        final Message firstMessage = Message.builder().body("test").build();
        final Message secondMessage = Message.builder().body("other").build();
        when(messageRetriever.retrieveMessage())
                .thenReturn(firstMessage)
                .thenReturn(secondMessage)
                .thenThrow(new InterruptedException());
        doReturn(mockFuture).when(mockExecutorService).submit(any(Runnable.class));
        when(mockFuture.get())
                .thenThrow(new InterruptedException())
                .thenReturn(null);

        // act
        controller.run();

        // assert
        assertThat(threadCompletedFuture.isDone()).isTrue();
    }

    @Test
    public void interruptedAgainWhileWaitingForMessageCompletesExceptionally() throws InterruptedException, ExecutionException {
        // arrange
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, mockExecutorService, threadCompletedFuture);
        final Message firstMessage = Message.builder().body("test").build();
        final Message secondMessage = Message.builder().body("other").build();
        when(messageRetriever.retrieveMessage())
                .thenReturn(firstMessage)
                .thenReturn(secondMessage)
                .thenThrow(new InterruptedException());
        doReturn(mockFuture).when(mockExecutorService).submit(any(Runnable.class));
        when(mockFuture.get())
                .thenThrow(new InterruptedException())
                .thenThrow(new InterruptedException());

        // act
        controller.run();

        // assert
        assertThat(threadCompletedFuture.isDone()).isTrue();
    }

    @Test
    public void errorThrownForMessageBeingCompletedAfterInterruptStillStopsController() throws InterruptedException, ExecutionException {
        // arrange
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, mockExecutorService, threadCompletedFuture);
        final Message firstMessage = Message.builder().build();
        when(messageRetriever.retrieveMessage())
                .thenReturn(firstMessage);
        doReturn(mockFuture).when(mockExecutorService).submit(any(Runnable.class));
        when(mockFuture.get())
                .thenThrow(new InterruptedException())
                .thenThrow(new ExecutionException(new RuntimeException("error")));

        // act
        controller.run();

        // assert
        assertThat(threadCompletedFuture.isDone()).isTrue();
    }

    @Test
    public void interruptingThreadDuringBackoffWillCompleteAndNotWaitForMessageToBeCompletedAgain() throws InterruptedException, ExecutionException {
        // arrange
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, mockExecutorService, threadCompletedFuture) {
            @Override
            void backoff() throws InterruptedException {
                Thread.currentThread().interrupt();
                super.backoff();
            }
        };
        final Message firstMessage = Message.builder().build();
        when(messageRetriever.retrieveMessage())
                .thenReturn(firstMessage);
        doReturn(mockFuture).when(mockExecutorService).submit(any(Runnable.class));
        when(mockFuture.get())
                .thenThrow(new ExecutionException(new RuntimeException("error")));

        // act
        controller.run();

        // assert
        assertThat(threadCompletedFuture.isDone()).isTrue();
        verify(mockFuture, times(1)).get();
    }

    @Test
    public void threadsRunningShouldBeCancelledIfInterruptIsPassedIn() throws InterruptedException, ExecutionException {
        // arrange
        final Message firstMessage = Message.builder().build();
        when(messageRetriever.retrieveMessage())
                .thenReturn(firstMessage);
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, mockExecutorService, threadCompletedFuture);
        doReturn(mockFuture).when(mockExecutorService).submit(any(Runnable.class));
        when(mockFuture.get()).thenThrow(new InterruptedException());
        controller.stopTriggered(true);

        // act
        controller.run();

        // assert
        verify(mockFuture, times(1)).cancel(true);
    }

    @Test
    public void threadsRunningShouldBeNotBeCancelledIfInterruptThreadsIsNotSet() throws InterruptedException, ExecutionException {
        // arrange
        final Message firstMessage = Message.builder().build();
        when(messageRetriever.retrieveMessage())
                .thenReturn(firstMessage);
        final CompletableFuture<Object> threadCompletedFuture = new CompletableFuture<>();
        final SingleThreadedMessageBroker.Controller controller = new SingleThreadedMessageBroker.SingleThreadMessageController(
                messageRetriever, messageProcessor, mockExecutorService, threadCompletedFuture);
        doReturn(mockFuture).when(mockExecutorService).submit(any(Runnable.class));
        when(mockFuture.get()).thenThrow(new InterruptedException());
        controller.stopTriggered(false);

        // act
        controller.run();

        // assert
        verify(mockFuture, never()).cancel(true);
    }

    @Test
    public void runsSingleThreadControllerOnStart() {
        // arrange
        final SingleThreadedMessageBroker broker = new SingleThreadedMessageBroker(messageRetriever, messageProcessor, mockExecutorService);

        // act
        broker.start();

        // assert
        verify(mockExecutorService).submit(any(SingleThreadedMessageBroker.SingleThreadMessageController.class));
    }
}
