package com.jashmore.sqs.resolver.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.resolver.MessageResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class BlockingMessageResolverTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MessageResolver delegateMessageResolver;
    
    private BlockingMessageResolver blockingMessageResolver;

    @Before
    public void setUp() {
        blockingMessageResolver = new BlockingMessageResolver(delegateMessageResolver);
    }

    @After
    public void tearDown() {
        Thread.interrupted();
    }

    @Test
    public void threadWillWaitUntilMessageIsResolvedBeforeExiting() {
        // arrange
        final CompletableFuture<?> delegateCompletableFuture = CompletableFuture.completedFuture("ignored");
        doReturn(delegateCompletableFuture).when(delegateMessageResolver).resolveMessage(any(Message.class));

        // act
        final CompletableFuture<?> future = blockingMessageResolver.resolveMessage(Message.builder().build());

        // assert
        assertThat(future).isEqualTo(delegateCompletableFuture);
        assertThat(future).isCompleted();
    }

    @Test
    public void delegateCompletableFutureThatThrowsInterruptedExceptionOnGetWillInterruptCurrentThread() throws ExecutionException, InterruptedException {
        // arrange
        final CompletableFuture<?> delegateCompletableFuture = mock(CompletableFuture.class);
        when(delegateCompletableFuture.get()).thenThrow(new InterruptedException());
        doReturn(delegateCompletableFuture).when(delegateMessageResolver).resolveMessage(any(Message.class));

        // act
        blockingMessageResolver.resolveMessage(Message.builder().build());

        // assert
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    public void delegateCompletableThatThrowsExceptionWillReturnFutureForConsumer() {
        // arrange
        final CompletableFuture<?> delegateCompletableFuture = new CompletableFuture<>();
        delegateCompletableFuture.completeExceptionally(new RuntimeException("Expected Exception"));
        doReturn(delegateCompletableFuture).when(delegateMessageResolver).resolveMessage(any(Message.class));

        // act
        final CompletableFuture<?> future = blockingMessageResolver.resolveMessage(Message.builder().build());

        // assert
        assertThat(future).isCompletedExceptionally();
    }
}