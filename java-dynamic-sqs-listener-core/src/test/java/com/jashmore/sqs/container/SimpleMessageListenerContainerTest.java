package com.jashmore.sqs.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.resolver.AsyncMessageResolver;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SimpleMessageListenerContainerTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MessageRetriever messageRetriever;
    
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
    private SimpleMessageListenerContainerProperties containerProperties;

    @Test
    public void passedInIdentifierIsReturnedFromGetIdentifier() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("id", null, null, null);

        // act
        final String actualIdentifier = container.getIdentifier();

        // assert
        assertThat(actualIdentifier).isEqualTo("id");
    }

    @Test
    public void onStartTheMessageBrokerIsStarted() {
        // arrange
        final SimpleMessageListenerContainer container = buildContainer();

        // act
        container.start();

        // assert
        verify(messageBroker).run();
    }

    @Test
    public void whenContainerIsAlreadyStartedTheMessageBrokerAndMessageRetrieverAreNotStartedAgain() {
        // arrange
        final SimpleMessageListenerContainer container = buildContainerWithMockExecutorService();

        // act
        container.start();
        container.start();

        // assert
        verify(asyncMessageRetriever, times(1)).run();
        verify(messageBroker, times(1)).run();
    }

    @Test
    public void nonAsyncComponentsWillNotAttemptToStartBackgroundThreadsForThem() {
        // arrange
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("id", messageRetriever, messageBroker, messageResolver,
                containerProperties, () -> mockExecutorService);
        mockExecutorServiceRunsRunnables();

        // act
        container.start();

        // assert
        verify(mockExecutorService, times(1)).submit(any(Runnable.class));
    }

    @Test
    public void stoppingContainerWhenNotRunningDoesNothing() {
        // arrange
        final SimpleMessageListenerContainer container = buildContainer();

        // act
        container.stop();

        // assert
        verify(mockExecutorService, never()).submit(messageBroker);
    }

    @Test
    public void threadInterruptedDuringStartWillKeepThreadInterruptedAfterStartup() {
        // arrange
        final SimpleMessageListenerContainer container = buildContainerWithMockExecutorService();

        // act
        Thread.currentThread().interrupt();
        container.start();

        // assert
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    public void stoppingContainerWhenRunningWillStopTheExecutorServiceAndWaitUntilFinished() throws Exception {
        // arrange
        final SimpleMessageListenerContainer container = buildContainerWithMockExecutorService();
        when(containerProperties.getShutdownRetryLimit()).thenReturn(0);
        when(containerProperties.getShutdownTimeout()).thenReturn(1L);
        when(containerProperties.getShutdownTimeUnit()).thenReturn(TimeUnit.MINUTES);
        container.start();

        // act
        container.stop();

        // assert
        verify(mockExecutorService).shutdownNow();
        verify(mockExecutorService).awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void threadInterruptedWhileWaitingForExecutorServiceShutdownWillInterruptCurrentThread() throws Exception {
        // arrange
        when(containerProperties.getShutdownRetryLimit()).thenReturn(0);
        when(containerProperties.getShutdownTimeout()).thenReturn(1L);
        when(containerProperties.getShutdownTimeUnit()).thenReturn(TimeUnit.MINUTES);
        final SimpleMessageListenerContainer container = buildContainerWithMockExecutorService();
        container.start();
        when(mockExecutorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

        // act
        container.stop();

        // assert
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    public void stoppingAlreadyStoppedContainerWillDoNothing() {
        // arrange
        final SimpleMessageListenerContainer container = buildContainerWithMockExecutorService();
        container.start();

        // act
        container.stop();
        container.stop();

        // assert
        verify(mockExecutorService, times(1)).shutdownNow();
    }

    @Test
    public void asyncMessageResolverWillBeStartedOnBackgroundThreadWhenStartCalled() {
        // arrange
        final SimpleMessageListenerContainer container = buildContainer();

        // act
        container.start();

        // assert
        verify(asyncMessageResolver).run();
    }

    @Test
    public void whenShutdownDoesNotTerminateInTimePeriodRetryAttemptsWillBeMade() throws InterruptedException {
        // arrange
        final SimpleMessageListenerContainer container = buildContainerWithMockExecutorService();
        when(containerProperties.getShutdownRetryLimit()).thenReturn(2);
        when(containerProperties.getShutdownTimeout()).thenReturn(1L);
        when(containerProperties.getShutdownTimeUnit()).thenReturn(TimeUnit.SECONDS);
        container.start();
        when(mockExecutorService.awaitTermination(1, TimeUnit.SECONDS))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);

        // act
        container.stop();

        // assert
        verify(mockExecutorService, times(3)).shutdownNow();
        verify(mockExecutorService, times(3)).awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void backgroundThreadThatDoesNotInterruptWillHaveItInterruptedAgainOnRetry() {
        // arrange
        final AtomicBoolean didQuit = new AtomicBoolean(false);
        final AsyncMessageRetriever asyncMessageRetriever = new AsyncMessageRetriever() {
            @Override
            public Message retrieveMessage() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void run() {
                boolean shouldQuitOnNextInterrupt = false;
                while (true) {
                    try {
                        log.debug("Sleeping");
                        Thread.sleep(100000);
                    } catch (InterruptedException e) {
                        log.debug("Interrupted");
                        if (shouldQuitOnNextInterrupt) {
                            log.debug("Quiting");
                            didQuit.set(true);
                            return;
                        }
                        log.debug("Quiting next interruption");
                        shouldQuitOnNextInterrupt = true;
                    }
                }
            }
        };
        when(containerProperties.getShutdownTimeout()).thenReturn(1L);
        when(containerProperties.getShutdownTimeUnit()).thenReturn(TimeUnit.SECONDS);
        when(containerProperties.getShutdownRetryLimit()).thenReturn(1);
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(
                "id", asyncMessageRetriever, messageBroker, messageResolver, containerProperties);
        container.start();

        // act
        container.stop();

        // assert
        assertThat(didQuit).isTrue();
    }

    private SimpleMessageListenerContainer buildContainer() {
        return new SimpleMessageListenerContainer("id", asyncMessageRetriever, messageBroker, asyncMessageResolver, containerProperties);
    }

    private SimpleMessageListenerContainer buildContainerWithMockExecutorService() {
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer("id", asyncMessageRetriever, messageBroker, asyncMessageResolver,
                containerProperties, () -> mockExecutorService);
        mockExecutorServiceRunsRunnables();

        return container;
    }

    private void mockExecutorServiceRunsRunnables() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(mockExecutorService).submit(any(Runnable.class));
    }
}
