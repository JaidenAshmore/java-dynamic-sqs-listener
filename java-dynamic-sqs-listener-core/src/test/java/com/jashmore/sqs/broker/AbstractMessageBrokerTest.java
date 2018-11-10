package com.jashmore.sqs.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jashmore.sqs.broker.AbstractMessageBroker.Controller;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class AbstractMessageBrokerTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ExecutorService mockExecutorService;

    @Mock
    private Controller mockController;

    @Mock
    private Future<?> mockFuture;

    private MessageBroker messageBroker;

    @Before
    public void setUp() {
        messageBroker = new AbstractMessageBroker(mockExecutorService) {
            @Override
            protected Controller createController(final CompletableFuture<Object> controllerFinishedFuture) {
                return mockController;
            }
        };
    }

    @Test
    public void controllerThreadShouldBeSubmittedOnStart() {
        // act
        messageBroker.start();

        // assert
        verify(mockExecutorService).submit(mockController);
    }

    @Test
    public void containerThatIsStartedTwiceDoesNotPerformStartupAgain() {
        // arrange
        doReturn(mockFuture).when(mockExecutorService).submit(mockController);

        // act
        messageBroker.start();
        messageBroker.start();

        // assert
        verify(mockExecutorService, times(1)).submit(mockController);
    }

    @Test
    public void stoppingContainerWhenHasNotStartedDoesNothing() throws InterruptedException, ExecutionException {
        // act
        final Future<?> stopFuture = messageBroker.stop();

        // assert
        assertThat(stopFuture.get()).isEqualTo("Not running");
    }

    @Test
    public void stoppingContainerWillCancelController() {
        // arrange
        doReturn(mockFuture).when(mockExecutorService).submit(mockController);

        // act
        messageBroker.start();
        messageBroker.stop(true);

        // assert
        verify(mockFuture).cancel(true);
        verify(mockController).stopTriggered(true);
    }
}
