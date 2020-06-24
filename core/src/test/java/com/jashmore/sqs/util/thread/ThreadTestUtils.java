package com.jashmore.sqs.util.thread;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ThreadTestUtils {

    public static void startRunnableInThread(final Runnable runnable, final BlockingConsumer<Thread> threadConsumer) {
        final Thread thread = new Thread(runnable);
        try {
            thread.start();
            threadConsumer.accept(thread);
        } catch (final Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            thread.interrupt();
        }
    }

    @SuppressWarnings("BusyWait")
    public static void waitUntilThreadInState(Thread thread, Thread.State expectedState) throws InterruptedException {
        int numberOfTimesCompleted = 0;
        while (thread.getState() != expectedState && numberOfTimesCompleted < 60) {
            Thread.sleep(100);
            numberOfTimesCompleted++;
        }

        assertThat(thread.getState()).isEqualTo(expectedState);
    }

    @FunctionalInterface
    public interface BlockingConsumer<T> {
        void accept(T object) throws Exception;
    }
}
