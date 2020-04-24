package com.jashmore.sqs.util.thread;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class ThreadUtilsTest {

    @Test
    void anyThreadCreatedUsingFactoryWillBeNamedCorrectly() throws InterruptedException, ExecutionException, TimeoutException {
        // arrange
        final ThreadFactory threadFactory = ThreadUtils.threadFactory("thread-name");

        // act
        final Future<String> threadNameFuture = Executors.newSingleThreadExecutor(threadFactory).submit(() -> Thread.currentThread().getName());
        final String threadName = threadNameFuture.get(1, TimeUnit.SECONDS);

        // assert
        assertThat(threadName).startsWith("thread-name");
    }
}