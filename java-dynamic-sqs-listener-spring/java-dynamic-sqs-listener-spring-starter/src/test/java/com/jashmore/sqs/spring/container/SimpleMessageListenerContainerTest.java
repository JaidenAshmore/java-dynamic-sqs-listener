package com.jashmore.sqs.spring.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
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
    private ExecutorService executorService;

    @Mock
    private Future<Object> messageBrokerStoppedFuture;

    @Mock
    private Future<Object> asyncMessageRetrieverStoppedFuture;

    @Mock
    private Future<?> messageResolverThreadFuture;

    @Before
    public void setUp() {
        when(messageBroker.stop()).thenReturn(messageBrokerStoppedFuture);
        when(asyncMessageRetriever.stop()).thenReturn(asyncMessageRetrieverStoppedFuture);
    }

    @Test
    public void identifierPassedIsTheIdentifierForTheContainer() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);

        // assert
        assertThat(container.getIdentifier()).isEqualTo("identifier");
    }

    @Test
    public void onStartTheMessageBrokerIsStarted() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);

        // act
        container.start();

        // assert
        verify(messageBroker).start();
    }

    @Test
    public void whenContainerIsAlreadyStartedTheMessageBrokerAndMessageRetrieverAreNotStartedAgain() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);

        // act
        container.start();
        container.start();

        // assert
        verify(asyncMessageRetriever, times(1)).start();
        verify(messageBroker, times(1)).start();
    }

    @Test
    public void stoppingContainerWhenNotRunningDoesNothing() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);

        // act
        container.stop();

        // assert
        verify(messageBroker, never()).stop();
    }

    @Test
    public void stoppingContainerWhenRunningWillStopAsyncMessageRetrievers() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);
        container.start();

        // act
        container.stop();

        // assert
        verify(asyncMessageRetriever).stop();
    }

    @Test
    public void stoppingContainerWhenRunningWillStopTheMessageBroker() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);
        container.start();

        // act
        container.stop();

        // assert
        verify(messageBroker).stop();
    }

    @Test
    public void stoppingContainerWhenRunningWillWaitUntilMessageBrokerIsStopped() throws InterruptedException, ExecutionException {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);
        container.start();

        // act
        container.stop();

        // assert
        verify(messageBrokerStoppedFuture).get();
    }

    @Test
    public void stoppingContainerWhenRunningWillWaitUntilAsyncMessageRetrieverIsStopped() throws InterruptedException, ExecutionException {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);
        container.start();

        // act
        container.stop();

        // assert
        verify(asyncMessageRetrieverStoppedFuture).get();
    }

    @Test
    public void exceptionThrownWhileStoppingAsyncMessageRetrieverWillNotBubbleException() throws InterruptedException, ExecutionException {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);
        container.start();
        when(asyncMessageRetrieverStoppedFuture.get()).thenThrow(new ExecutionException("test", new IllegalArgumentException()));

        // act
        container.stop();
    }


    @Test
    public void exceptionThrownWhileStoppingMessageBrokerWillNotBubbleException() throws InterruptedException, ExecutionException {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);
        container.start();
        when(messageBrokerStoppedFuture.get()).thenThrow(new ExecutionException("test", new IllegalArgumentException()));

        // act
        container.stop();
    }

    @Test
    public void stoppingAlreadyStoppedContainerWillDoNothing() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker, messageResolver);
        container.start();

        // act
        container.stop();
        container.stop();

        // assert
        verify(messageBroker, times(1)).stop();
        verify(asyncMessageRetriever, times(1)).stop();
    }

    @Test
    public void asyncMessageResolverWillBeStartedOnBackgroundThreadWhenStartCalled() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever,
                messageBroker, asyncMessageResolver, executorService);

        // act
        container.start();

        // assert
        verify(executorService).submit(asyncMessageResolver);
    }

    @Test
    public void whenAsyncMessageResolverStartedItWillCancelThreadWhenContainerIsStopped() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever,
                messageBroker, asyncMessageResolver, executorService);
        doReturn(messageResolverThreadFuture).when(executorService).submit(asyncMessageResolver);
        container.start();

        // act
        container.stop();

        // assert
        verify(messageResolverThreadFuture).cancel(true);
    }
}
