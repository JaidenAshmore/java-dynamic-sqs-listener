package com.jashmore.sqs.spring.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SimpleMessageListenerContainerTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AsyncMessageRetriever asyncMessageRetriever;

    @Mock
    private MessageBroker messageBroker;

    @Mock
    private Future<Object> messageBrokerStoppedFuture;

    @Mock
    private Future<Object> asyncMessageRetrieverStoppedFuture;

    @Before
    public void setUp() {
        when(messageBroker.stop()).thenReturn(messageBrokerStoppedFuture);
        when(asyncMessageRetriever.stop()).thenReturn(asyncMessageRetrieverStoppedFuture);
    }

    @Test
    public void identifierPassedIsTheIdentifierForTheContainer() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);

        // assert
        assertThat(container.getIdentifier()).isEqualTo("identifier");
    }

    @Test
    public void onStartAsyncMessageRetrieversAreStarted() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);

        // act
        container.start();

        // assert
        verify(asyncMessageRetriever).start();
    }

    @Test
    public void onStartTheMessageBrokerIsStarted() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", messageBroker);

        // act
        container.start();

        // assert
        verify(messageBroker).start();
    }

    @Test
    public void whenContainerIsAlreadyStartedTheMessageBrokerAndMessageRetrieverAreNotStartedAgain() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);

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
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);

        // act
        container.stop();

        // assert
        verify(messageBroker, never()).stop();
    }

    @Test
    public void stoppingContainerWhenRunningWillStopAsyncMessageRetrievers() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);
        container.start();

        // act
        container.stop();

        // assert
        verify(asyncMessageRetriever).stop();
    }

    @Test
    public void stoppingContainerWhenRunningWillStopTheMessageBroker() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", messageBroker);
        container.start();

        // act
        container.stop();

        // assert
        verify(messageBroker).stop();
    }

    @Test
    public void stoppingContainerWhenRunningWillWaitUntilMessageBrokerIsStopped() throws InterruptedException, ExecutionException {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);
        container.start();

        // act
        container.stop();

        // assert
        verify(messageBrokerStoppedFuture).get();
    }

    @Test
    public void stoppingContainerWhenRunningWillWaitUntilAsyncMessageRetrieverIsStopped() throws InterruptedException, ExecutionException {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);
        container.start();

        // act
        container.stop();

        // assert
        verify(asyncMessageRetrieverStoppedFuture).get();
    }

    @Test
    public void exceptionThrownWhileStoppingAsyncMessageRetrieverWillNotBubbleException() throws InterruptedException, ExecutionException {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);
        container.start();
        when(asyncMessageRetrieverStoppedFuture.get()).thenThrow(new ExecutionException("test", new IllegalArgumentException()));

        // act
        container.stop();
    }


    @Test
    public void exceptionThrownWhileStoppingMessageBrokerWillNotBubbleException() throws InterruptedException, ExecutionException {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);
        container.start();
        when(messageBrokerStoppedFuture.get()).thenThrow(new ExecutionException("test", new IllegalArgumentException()));

        // act
        container.stop();
    }

    @Test
    public void stoppingAlreadyStoppedContainerWillDoNothing() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("identifier", asyncMessageRetriever, messageBroker);
        container.start();

        // act
        container.stop();
        container.stop();

        // assert
        verify(messageBroker, times(1)).stop();
        verify(asyncMessageRetriever, times(1)).stop();
    }
}
