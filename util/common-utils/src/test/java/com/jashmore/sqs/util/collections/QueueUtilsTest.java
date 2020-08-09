package com.jashmore.sqs.util.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QueueUtilsTest {

    @Nested
    class Drain {

        @Test
        void whenNumberOfElementsPresentInQueueWillExtractThoseElements() throws InterruptedException {
            // arrange
            final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            queue.put("one");
            queue.put("two");
            final List<String> list = new ArrayList<>();

            // act
            QueueUtils.drain(queue, list, 2, Duration.ofMillis(0));

            // assert
            assertThat(list).containsExactly("one", "two");
            assertThat(queue).isEmpty();
        }

        @Test
        void whenMoreElementsPresentInQueueWillExtractOnlyTheRequiredElements() throws InterruptedException {
            // arrange
            final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            queue.put("one");
            queue.put("two");
            final List<String> list = new ArrayList<>();

            // act
            QueueUtils.drain(queue, list, 1, Duration.ofMillis(0));

            // assert
            assertThat(list).containsExactly("one");
            assertThat(queue).containsExactly("two");
        }

        @Test
        void whenQueueDoesNotHaveEnoughElementsItWillWaitUntilPresent() throws InterruptedException, TimeoutException, ExecutionException {
            // arrange
            final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            queue.put("one");
            queue.put("two");
            final List<String> list = new ArrayList<>();

            // act
            final CompletableFuture<Void> future = runInBackgroundThread(() -> QueueUtils.drain(queue, list, 3, Duration.ofMillis(500)));
            Thread.sleep(100);
            assertThat(future).isNotDone();
            queue.put("three");
            future.get(1, TimeUnit.SECONDS);

            // assert
            assertThat(list).containsExactly("one", "two", "three");
            assertThat(queue).isEmpty();
        }

        @Test
        void whenNoElementsPlacedIntoQueueWithinLimitWillAddNoElementsToList() throws InterruptedException {
            // arrange
            final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            final List<String> list = new ArrayList<>();

            // act
            final long startTime = System.currentTimeMillis();
            QueueUtils.drain(queue, list, 1, Duration.ofMillis(500));

            // assert
            assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(500);
            assertThat(list).isEmpty();
        }

        @Test
        void whenNotEnoughElementsPlacedIntoQueueWithinLimitWillOnlyAddElementsFound()
            throws InterruptedException, TimeoutException, ExecutionException {
            // arrange
            final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            final List<String> list = new ArrayList<>();

            // act
            final long startTime = System.currentTimeMillis();
            final CompletableFuture<Void> future = runInBackgroundThread(() -> QueueUtils.drain(queue, list, 3, Duration.ofMillis(500)));
            Thread.sleep(100);
            queue.add("one");
            future.get(1, TimeUnit.SECONDS);

            // assert
            assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(500);
            assertThat(list).containsExactly("one");
        }
    }

    private CompletableFuture<Void> runInBackgroundThread(BlockingRunnable runnable) {
        return CompletableFuture.runAsync(
            () -> {
                try {
                    runnable.run();
                } catch (InterruptedException interruptedException) {
                    throw new RuntimeException(interruptedException);
                }
            }
        );
    }

    /**
     * Similar to a {@link Runnable} but it allows for {@link InterruptedException}s to be thrown.
     */
    @FunctionalInterface
    private interface BlockingRunnable {
        /**
         * Run the method.
         *
         * @throws InterruptedException if the thread was interrupted during execution
         */
        void run() throws InterruptedException;
    }
}
