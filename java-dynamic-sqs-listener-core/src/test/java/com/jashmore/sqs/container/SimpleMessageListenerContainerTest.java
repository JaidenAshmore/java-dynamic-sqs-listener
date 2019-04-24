package com.jashmore.sqs.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.resolver.AsyncMessageResolver;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SimpleMessageListenerContainerTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AsyncMessageRetriever asyncMessageRetriever;

    @Mock
    private MessageBroker messageBroker;

    @Mock
    private MessageResolver messageResolver;

    @Mock
    private AsyncMessageResolver asyncMessageResolver;

    @Mock
    private ExecutorService mockExecutorService;

    @Mock
    private Future<Object> messageBrokerStoppedFuture;

    private SimpleMessageListenerContainer container;

    @Before
    public void setUp() {
        when(messageBroker.stop()).thenReturn(messageBrokerStoppedFuture);

        container = new SimpleMessageListenerContainer(asyncMessageRetriever, messageBroker, messageResolver) {
            @Override
            ExecutorService getNewExecutorService() {
                return mockExecutorService;
            }
        };
    }

    @Test
    public void onStartTheMessageBrokerIsStarted() {
        // act
        container.start();

        // assert
        verify(messageBroker).start();
    }

    @Test
    public void whenContainerIsAlreadyStartedTheMessageBrokerAndMessageRetrieverAreNotStartedAgain() {
        // act
        container.start();
        container.start();

        // assert
        verify(mockExecutorService, times(1)).submit(any(AsyncMessageRetriever.class));
        verify(messageBroker, times(1)).start();
    }

    @Test
    public void stoppingContainerWhenNotRunningDoesNothing() {
        // act
        container.stop();

        // assert
        verify(messageBroker, never()).stop();
    }

    @Test
    public void stoppingContainerWhenRunningWillStopTheExecutorServiceAndWaitUntilFinished() throws Exception {
        // arrange
        container.start();

        // act
        container.stop();

        // assert
        verify(mockExecutorService).shutdownNow();
        verify(mockExecutorService).awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void threadInterruptedWhileWaitingForExecutorServiceShutdownWilLInterruptCurrentThread() throws Exception {
        // arrange
        container.start();
        when(mockExecutorService.awaitTermination(1, TimeUnit.MINUTES)).thenThrow(new InterruptedException());

        // act
        container.stop();

        // assert
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    public void stoppingContainerWhenRunningWillStopTheMessageBroker() {
        // arrange
        container.start();

        // act
        container.stop();

        // assert
        verify(messageBroker).stop();
    }

    @Test
    public void stoppingContainerWhenRunningWillWaitUntilMessageBrokerIsStopped() throws InterruptedException, ExecutionException {
        // arrange
        container.start();

        // act
        container.stop();

        // assert
        verify(messageBrokerStoppedFuture).get();
    }

    @Test
    public void stoppingAlreadyStoppedContainerWillDoNothing() {
        // arrange
        container.start();

        // act
        container.stop();
        container.stop();

        // assert
        verify(messageBroker, times(1)).stop();
        verify(mockExecutorService, times(1)).shutdownNow();
    }

    @Test
    public void asyncMessageResolverWillBeStartedOnBackgroundThreadWhenStartCalled() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(asyncMessageRetriever, messageBroker, asyncMessageResolver) {
            @Override
            ExecutorService getNewExecutorService() {
                return mockExecutorService;
            }
        };

        // act
        container.start();

        // assert
        verify(mockExecutorService).submit(asyncMessageResolver);
    }
}
