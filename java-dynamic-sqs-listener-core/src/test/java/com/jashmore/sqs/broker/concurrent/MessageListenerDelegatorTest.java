package com.jashmore.sqs.broker.concurrent;

public class MessageListenerDelegatorTest {
//    @Rule
//    public MockitoRule mockitoRule = MockitoJUnit.rule();
//
//    @Mock
//    private Supplier<MessageListener> messageListenerSupplier;
//
//    @Mock
//    private Supplier<Integer> concurrencyLevelSupplier;
//
//    @Mock
//    private Supplier<Integer> pollingPeriodInSecondsSupplier;
//
//    private ExecutorService executorService = Executors.newCachedThreadPool();
//
//    @Test
//    public void whenNoConcurrencyNoMessageListenersAreCreated() throws Exception {
//        // arrange
//        final CountDownLatch sleepEntered = new CountDownLatch(1);
//        final CountDownLatch sleepShouldExit = new CountDownLatch(1);
//        when(concurrencyLevelSupplier.get()).thenReturn(0);
//        final ConcurrentMessageListenerController messageListenerDelegator = sleepBlockingDelegator(sleepEntered, sleepShouldExit);
//
//        // act
//        final Future<?> delegatorFuture = executorService.submit(messageListenerDelegator);
//        sleepEntered.await(1, SECONDS);
//
//        // assert
//        try {
//            verify(messageListenerSupplier, never()).get();
//        } finally {
//            // cleanup
//            messageListenerDelegator.stop();
//            sleepShouldExit.countDown();
//            delegatorFuture.get(1, SECONDS);
//        }
//    }
//
//    @Test
//    public void onInitialLoadAllListenersAreBuilt() throws Exception {
//        // arrange
//        final CountDownLatch sleepingEnteredLatch = new CountDownLatch(1);
//        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
//        when(concurrencyLevelSupplier.get()).thenReturn(2);
//        final ConcurrentMessageListenerController messageListenerDelegator = sleepBlockingDelegator(sleepingEnteredLatch, testCompletedLatch);
//        final DefaultMessageListener firstListener = blockingMessageListener();
//        final DefaultMessageListener secondListener = blockingMessageListener();
//        when(messageListenerSupplier.get()).thenReturn(firstListener, secondListener);
//
//        // act
//        executorService.submit(messageListenerDelegator);
//        sleepingEnteredLatch.await(1, SECONDS);
//
//        // assert
//        try {
//            verify(messageListenerSupplier, times(2)).get();
//        } finally {
//            messageListenerDelegator.stop();
//            testCompletedLatch.countDown();
//        }
//    }
//
//    @Test
//    public void whenDelegatorStoppedAllThreadsAreStopped() throws Exception {
//        // arrange
//        final CountDownLatch sleepingEnteredLatch = new CountDownLatch(1);
//        final CountDownLatch sleepShouldEndLatch = new CountDownLatch(1);
//        when(concurrencyLevelSupplier.get()).thenReturn(2);
//        final ConcurrentMessageListenerController messageListenerDelegator = sleepBlockingDelegator(sleepingEnteredLatch, sleepShouldEndLatch);
//        final DefaultMessageListener firstListener = blockingMessageListener();
//        final DefaultMessageListener secondListener = blockingMessageListener();
//        when(messageListenerSupplier.get()).thenReturn(firstListener, secondListener);
//        final Future<?> delegatorFuture = executorService.submit(messageListenerDelegator);
//        sleepingEnteredLatch.await(1, SECONDS);
//
//        // act
//        messageListenerDelegator.stop();
//        sleepShouldEndLatch.countDown();
//        delegatorFuture.get(1, SECONDS);
//
//        // assert
//        verify(firstListener, times(1)).stop();
//        verify(secondListener, times(1)).stop();
//    }
//
//    @Test
//    public void whenMoreConcurrencyIsRequiredMoreListenersAreCreated() throws Exception {
//        // arrange
//        final CountDownLatch firstSleepingEntered = new CountDownLatch(1);
//        final CountDownLatch firstSleepShouldEndLatch = new CountDownLatch(1);
//        final CountDownLatch secondSleepingEntered = new CountDownLatch(1);
//        final CountDownLatch secondSleepShouldEndLatch = new CountDownLatch(1);
//        when(concurrencyLevelSupplier.get())
//                .thenReturn(2)
//                .thenReturn(3);
//        final ConcurrentMessageListenerController messageListenerDelegator = new ConcurrentMessageListenerController(messageListenerSupplier, concurrencyLevelSupplier,
//                pollingPeriodInSecondsSupplier, executorService) {
//            @Override
//            void sleepForPeriod() {
//                try {
//                    if (firstSleepingEntered.getCount() > 0) {
//                        firstSleepingEntered.countDown();
//                        firstSleepShouldEndLatch.await(1, SECONDS);
//                    } else {
//                        secondSleepingEntered.countDown();
//                        secondSleepShouldEndLatch.await(1, SECONDS);
//                    }
//                } catch (InterruptedException interruptedException) {
//                    throw new RuntimeException(interruptedException);
//                }
//            }
//        };
//        final DefaultMessageListener firstListener = blockingMessageListener();
//        final DefaultMessageListener secondListener = blockingMessageListener();
//        final DefaultMessageListener thirdListener = blockingMessageListener();
//        when(messageListenerSupplier.get()).thenReturn(firstListener, secondListener, thirdListener);
//        final Future<?> delegatorFuture = executorService.submit(messageListenerDelegator);
//        firstSleepingEntered.await(1, SECONDS);
//        verify(messageListenerSupplier, times(2)).get();
//
//        // act
//        firstSleepShouldEndLatch.countDown();
//        secondSleepingEntered.await(1, SECONDS);
//
//        // assert
//        verify(messageListenerSupplier, times(3)).get();
//
//        // cleanup
//        messageListenerDelegator.stop();
//        secondSleepShouldEndLatch.countDown();
//        delegatorFuture.get(1, SECONDS);
//    }
//
//    @Test
//    public void whenMoreConcurrencyIsRequiredTheExtraListenersAreCleanedUpWhenStopped() throws Exception {
//        // arrange
//        final CountDownLatch sleepEntered = new CountDownLatch(2);
//        final CountDownLatch sleepShouldExit = new CountDownLatch(1);
//        when(concurrencyLevelSupplier.get())
//                .thenReturn(1)
//                .thenReturn(2);
//        final ConcurrentMessageListenerController messageListenerDelegator = sleepBlockingDelegator(sleepEntered, sleepShouldExit);
//        final DefaultMessageListener firstListener = blockingMessageListener();
//        final DefaultMessageListener secondListenerCreatedLater = blockingMessageListener();
//        when(messageListenerSupplier.get()).thenReturn(firstListener, secondListenerCreatedLater);
//        final Future<?> delegatorFuture = executorService.submit(messageListenerDelegator);
//        sleepEntered.await(1, SECONDS);
//
//        // act
//        verify(firstListener, never()).stop();
//        verify(secondListenerCreatedLater, never()).stop();
//        messageListenerDelegator.stop();
//        sleepShouldExit.countDown();
//        delegatorFuture.get(1, SECONDS);
//
//        // assert
//        verify(firstListener).stop();
//        verify(secondListenerCreatedLater).stop();
//    }
//
//    @Test
//    public void whenLessConcurrencyIsRequiredTheExtraListenersAreStopped() throws Exception {
//        // arrange
//        final CountDownLatch sleepEntered = new CountDownLatch(2);
//        final CountDownLatch sleepShouldExit = new CountDownLatch(1);
//        when(concurrencyLevelSupplier.get())
//                .thenReturn(1)
//                .thenReturn(0);
//        final ConcurrentMessageListenerController messageListenerDelegator = sleepBlockingDelegator(sleepEntered, sleepShouldExit);
//        final DefaultMessageListener firstListener = blockingMessageListener();
//        final DefaultMessageListener secondListener = blockingMessageListener();
//        when(messageListenerSupplier.get()).thenReturn(firstListener, secondListener);
//
//        // act
//        final Future<?> delegatorFuture = executorService.submit(messageListenerDelegator);
//        sleepEntered.await(1, SECONDS);
//
//        // assert
//        try {
//            verify(firstListener).stop();
//            verify(secondListener, never()).stop();
//        } finally {
//            // cleanup
//            messageListenerDelegator.stop();
//            sleepShouldExit.countDown();
//            delegatorFuture.get(1, SECONDS);
//        }
//    }
//
//    private ConcurrentMessageListenerController sleepBlockingDelegator(final CountDownLatch sleepingEnteredLatch,
//                                                                       final CountDownLatch sleepShouldEndLatch) {
//        return new ConcurrentMessageListenerController(messageListenerSupplier, concurrencyLevelSupplier, pollingPeriodInSecondsSupplier, executorService) {
//            @Override
//            void sleepForPeriod() {
//                sleepingEnteredLatch.countDown();
//                if (sleepingEnteredLatch.getCount() == 0) {
//                    try {
//                        sleepShouldEndLatch.await(1, SECONDS);
//                    } catch (final InterruptedException interruptedException) {
//                        throw new RuntimeException(interruptedException);
//                    }
//                }
//            }
//        };
//    }
//
//    private DefaultMessageListener blockingMessageListener() {
//        final CountDownLatch waitUntilStopLatch = new CountDownLatch(1);
//        final DefaultMessageListener messageListener = mock(DefaultMessageListener.class);
//        doAnswer(invocation -> waitUntilStopLatch.await(1, SECONDS)).when(messageListener).run();
//        doAnswer(invocation -> {
//            waitUntilStopLatch.countDown();
//            return null;
//        }).when(messageListener).stop();
//        return messageListener;
//    }
}
