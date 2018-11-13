package com.jashmore.sqs.broker;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jashmore.sqs.broker.AbstractMessageBroker.Controller;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class AbstractMessageBrokerTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
    public void containerThatIsStartedTwiceThrowsIllegalStateExceptionSecondTime() {
        // arrange
        doReturn(mockFuture).when(mockExecutorService).submit(mockController);
        messageBroker.start();
        expectedException.expect(IllegalStateException.class);

        // act
        messageBroker.start();

        // assert
        verify(mockExecutorService, times(1)).submit(mockController);
    }

    @Test(expected = IllegalStateException.class)
    public void stoppingContainerWhenHasNotStartedDoesNothing() {
        // act
        messageBroker.stop();
    }

    @Test
    public void stoppingContainerWillCancelController() {
        // arrange
        doReturn(mockFuture).when(mockExecutorService).submit(mockController);

        // act
        messageBroker.start();
        messageBroker.stopWithChildrenThreadsInterrupted();

        // assert
        verify(mockFuture).cancel(true);
        verify(mockController).stopTriggered(true);
    }
}
