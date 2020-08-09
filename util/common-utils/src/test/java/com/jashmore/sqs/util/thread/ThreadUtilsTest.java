package com.jashmore.sqs.util.thread;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ThreadUtilsTest {

    @Nested
    class MultiNamedThreadFactory {

        @Test
        void anyThreadCreatedUsingFactoryWillBeNamedCorrectly() throws InterruptedException, ExecutionException, TimeoutException {
            // arrange
            final ThreadFactory threadFactory = ThreadUtils.multiNamedThreadFactory("thread-name");

            // act
            final Future<String> threadNameFuture = Executors
                .newSingleThreadExecutor(threadFactory)
                .submit(() -> Thread.currentThread().getName());
            final String threadName = threadNameFuture.get(1, TimeUnit.SECONDS);

            // assert
            assertThat(threadName).isEqualTo("thread-name-0");
        }

        @Test
        void multipleThreadsWillIncreaseTheThreadNameCount() throws InterruptedException, ExecutionException, TimeoutException {
            // arrange
            final ThreadFactory threadFactory = ThreadUtils.multiNamedThreadFactory("thread-name");

            // act
            Executors.newSingleThreadExecutor(threadFactory).submit(() -> Thread.currentThread().getName());
            final Future<String> threadNameFuture = Executors
                .newSingleThreadExecutor(threadFactory)
                .submit(() -> Thread.currentThread().getName());
            final String threadName = threadNameFuture.get(1, TimeUnit.SECONDS);

            // assert
            assertThat(threadName).isEqualTo("thread-name-1");
        }
    }

    @Nested
    class SingleNamedThreadFactory {

        @Test
        void allThreadsWillHaveSameName() throws InterruptedException, ExecutionException, TimeoutException {
            // arrange
            final ThreadFactory threadFactory = ThreadUtils.singleNamedThreadFactory("thread-name");

            // act
            final Future<String> threadNameFuture = Executors
                .newSingleThreadExecutor(threadFactory)
                .submit(() -> Thread.currentThread().getName());
            final Future<String> secondThreadNameFuture = Executors
                .newSingleThreadExecutor(threadFactory)
                .submit(() -> Thread.currentThread().getName());
            final String threadName = threadNameFuture.get(1, TimeUnit.SECONDS);
            final String secondThreadName = secondThreadNameFuture.get(1, TimeUnit.SECONDS);

            // assert
            assertThat(secondThreadName).isEqualTo("thread-name");
            assertThat(threadName).isEqualTo("thread-name");
        }
    }
}
